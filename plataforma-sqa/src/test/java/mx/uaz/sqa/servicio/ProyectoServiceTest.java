package mx.uaz.sqa.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mx.uaz.sqa.dominio.excepcion.DatosInvalidosException;
import mx.uaz.sqa.dominio.excepcion.ProyectoDuplicadoException;
import mx.uaz.sqa.dominio.excepcion.RecursoNoEncontradoException;
import mx.uaz.sqa.dominio.modelo.Periodo;
import mx.uaz.sqa.dominio.modelo.Proyecto;
import mx.uaz.sqa.persistencia.ProyectoRepository;
import mx.uaz.sqa.servicio.dto.DatosProyecto;
import mx.uaz.sqa.servicio.dto.ProyectoResumen;
import mx.uaz.sqa.soporte.Fabrica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("CU01 – Gestionar proyecto de software por periodo (servicio)")
class ProyectoServiceTest {

    @Mock
    private ProyectoRepository proyectos;

    private ProyectoService servicio;
    private Proyecto existente;

    @BeforeEach
    void preparar() {
        servicio = new ProyectoService(proyectos, Fabrica.reloj());
        existente = Fabrica.conId(Fabrica.proyecto(), 1L);
    }

    @Test
    @DisplayName("RS-01: crea un proyecto válido cuando el periodo es nuevo o consistente")
    void creaProyecto() {
        Periodo p = existente.getPeriodo();
        DatosProyecto datos = new DatosProyecto(
                "Nuevo Sistema",
                "Descripción",
                "Java, Spring",
                p.getEtiqueta(),
                p.getFechaInicio(),
                p.getFechaFin()
        );

        when(proyectos.existsByNombreIgnoreCaseAndPeriodoEtiquetaIgnoreCase("Nuevo Sistema", p.getEtiqueta()))
                .thenReturn(false);
        when(proyectos.findByPeriodoEtiquetaIgnoreCase(p.getEtiqueta()))
                .thenReturn(List.of(existente));
        when(proyectos.save(any(Proyecto.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Proyecto creado = servicio.crear(datos);

        assertEquals("Nuevo Sistema", creado.getNombre());
        assertEquals(p.getEtiqueta(), creado.getPeriodo().getEtiqueta());
        verify(proyectos).save(any(Proyecto.class));
    }

    @Test
    @DisplayName("RS-01: rechaza proyecto duplicado en el mismo periodo")
    void rechazaProyectoDuplicadoAlCrear() {
        Periodo p = existente.getPeriodo();
        DatosProyecto datos = new DatosProyecto(
                existente.getNombre(),
                "Descripción",
                "Java",
                p.getEtiqueta(),
                p.getFechaInicio(),
                p.getFechaFin()
        );

        when(proyectos.existsByNombreIgnoreCaseAndPeriodoEtiquetaIgnoreCase(existente.getNombre(), p.getEtiqueta()))
                .thenReturn(true);

        assertThrows(ProyectoDuplicadoException.class, () -> servicio.crear(datos));
        verify(proyectos, never()).save(any());
    }

    // Issue #3: pruebas de consistencia temporal del periodo en creación y actualización.
    @Test
    @DisplayName("Issue #3: rechaza crear proyecto si el periodo ya existe con fecha de inicio o fin distinta")
    void rechazaCrearConFechasDePeriodoInconsistentes() {
        Periodo p = existente.getPeriodo();
        when(proyectos.existsByNombreIgnoreCaseAndPeriodoEtiquetaIgnoreCase("Otro Proyecto", p.getEtiqueta()))
                .thenReturn(false);
        when(proyectos.findByPeriodoEtiquetaIgnoreCase(p.getEtiqueta()))
                .thenReturn(List.of(existente));

        // 1. Discrepancia en fecha de inicio
        DatosProyecto datosInicioDistinto = new DatosProyecto(
                "Otro Proyecto",
                "Desc",
                "Java",
                p.getEtiqueta(),
                p.getFechaInicio().plusDays(1),
                p.getFechaFin()
        );

        DatosInvalidosException errorInicio = assertThrows(
                DatosInvalidosException.class,
                () -> servicio.crear(datosInicioDistinto)
        );
        assertEquals("etiquetaPeriodo", errorInicio.getCampo());

        // 2. Discrepancia en fecha de fin
        DatosProyecto datosFinDistinto = new DatosProyecto(
                "Otro Proyecto",
                "Desc",
                "Java",
                p.getEtiqueta(),
                p.getFechaInicio(),
                p.getFechaFin().minusDays(1)
        );

        assertThrows(DatosInvalidosException.class, () -> servicio.crear(datosFinDistinto));
        verify(proyectos, never()).save(any());
    }

    @Test
    @DisplayName("Issue #3: al actualizar permite cambiar fechas si es el único proyecto del periodo, pero rechaza si choca con otro")
    void validaConsistenciaPeriodoAlActualizar() {
        Periodo p = existente.getPeriodo();
        Proyecto otroEnMismoPeriodo = Fabrica.conId(Fabrica.proyecto(), 2L);

        when(proyectos.findById(1L)).thenReturn(Optional.of(existente));
        when(proyectos.existsByNombreIgnoreCaseAndPeriodoEtiquetaIgnoreCaseAndIdNot("Plataforma SQA", p.getEtiqueta(), 1L))
                .thenReturn(false);

        // Caso A: Solo existe el proyecto 1L en ese periodo (se ignora a sí mismo y permite actualizar)
        when(proyectos.findByPeriodoEtiquetaIgnoreCase(p.getEtiqueta()))
                .thenReturn(List.of(existente));

        LocalDate nuevaFin = p.getFechaFin().plusDays(5);
        DatosProyecto datosActualizados = new DatosProyecto(
                "Plataforma SQA",
                "Nueva desc",
                "Spring Boot",
                p.getEtiqueta(),
                p.getFechaInicio(),
                nuevaFin
        );

        Proyecto actualizado = servicio.actualizar(1L, datosActualizados);
        assertEquals(nuevaFin, actualizado.getPeriodo().getFechaFin());

        // Caso B: Hay otro proyecto (2L) en el mismo periodo con las fechas originales -> debe rechazar
        when(proyectos.findByPeriodoEtiquetaIgnoreCase(p.getEtiqueta()))
                .thenReturn(List.of(existente, otroEnMismoPeriodo));

        DatosProyecto datosConflictivos = new DatosProyecto(
                "Plataforma SQA",
                "Nueva desc",
                "Spring Boot",
                p.getEtiqueta(),
                p.getFechaInicio(),
                p.getFechaFin().plusDays(10)
        );

        assertThrows(DatosInvalidosException.class, () -> servicio.actualizar(1L, datosConflictivos));
    }

    @Test
    @DisplayName("RS-02: rechaza actualizar si el nuevo nombre duplica otro proyecto del periodo")
    void rechazaDuplicadoAlActualizar() {
        Periodo p = existente.getPeriodo();
        when(proyectos.findById(1L)).thenReturn(Optional.of(existente));
        when(proyectos.existsByNombreIgnoreCaseAndPeriodoEtiquetaIgnoreCaseAndIdNot("Duplicado", p.getEtiqueta(), 1L))
                .thenReturn(true);

        DatosProyecto datos = new DatosProyecto(
                "Duplicado", "Desc", "Java", p.getEtiqueta(), p.getFechaInicio(), p.getFechaFin()
        );

        assertThrows(ProyectoDuplicadoException.class, () -> servicio.actualizar(1L, datos));
    }

    @Test
    @DisplayName("Activa, desactiva, lista y obtiene resumen de proyectos")
    void operacionesDeConsultaYEstado() {
        when(proyectos.findById(1L)).thenReturn(Optional.of(existente));
        when(proyectos.findById(99L)).thenReturn(Optional.empty());
        when(proyectos.findAll(any(Sort.class))).thenReturn(List.of(existente));

        servicio.cambiarActivo(1L, false);
        assertFalse(existente.isActivo());

        servicio.cambiarActivo(1L, true);
        assertTrue(existente.isActivo());

        ProyectoResumen resumen = servicio.obtenerResumen(1L);
        assertEquals(existente.getNombre(), resumen.nombre());

        Map<String, List<ProyectoResumen>> agrupados = servicio.listarAgrupadosPorPeriodo();
        assertEquals(1, agrupados.size());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(99L));
    }
}