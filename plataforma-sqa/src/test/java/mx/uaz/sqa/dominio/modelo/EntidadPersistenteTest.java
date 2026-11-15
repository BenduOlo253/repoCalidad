package mx.uaz.sqa.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import mx.uaz.sqa.soporte.Fabrica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica que EntidadPersistente identifique entidades por su clave primaria
 * en colecciones y mapas (CU05, CU06 / RS-09, RS-10).
 */
@DisplayName("EntidadPersistente: equals y hashCode por id (CU05, CU06 / RS-09, RS-10)")
class EntidadPersistenteTest {

    // -------------------------------------------------------------------------
    // equals
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Dos instancias distintas con el mismo id son iguales")
    void dosInstanciasConMismoIdSonIguales() {
        Proyecto proyectoA = Fabrica.conId(Fabrica.proyecto(), 1L);
        Proyecto proyectoB = Fabrica.conId(Fabrica.proyecto(), 1L);

        assertEquals(proyectoA, proyectoB,
                "Instancias desacopladas con el mismo id deben ser iguales");
    }

    @Test
    @DisplayName("Dos instancias con distinto id no son iguales")
    void dosInstanciasConDistintoIdNoSonIguales() {
        Proyecto proyectoA = Fabrica.conId(Fabrica.proyecto(), 1L);
        Proyecto proyectoB = Fabrica.conId(Fabrica.proyecto(), 2L);

        assertNotEquals(proyectoA, proyectoB);
    }

    @Test
    @DisplayName("Una entidad sin id solo es igual a sí misma")
    void entidadSinIdSoloEsIgualASiMisma() {
        Proyecto proyectoA = Fabrica.proyecto();
        Proyecto proyectoB = Fabrica.proyecto();

        assertEquals(proyectoA, proyectoA, "Una entidad sin id debe ser igual a sí misma");
        assertNotEquals(proyectoA, proyectoB,
                "Dos entidades sin id distintas no deben ser iguales");
    }

    @Test
    @DisplayName("Entidades de tipos distintos con el mismo id no son iguales")
    void tiposDistintosConMismoIdNoSonIguales() {
        Proyecto proyecto = Fabrica.conId(Fabrica.proyecto(), 1L);
        Metrica metrica  = Fabrica.conId(Fabrica.metricaCobertura(Fabrica.proyecto()), 1L);

        assertNotEquals(proyecto, metrica,
                "Tipos diferentes no deben ser iguales aunque compartan id");
    }

    // -------------------------------------------------------------------------
    // hashCode
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Dos instancias con el mismo id tienen el mismo hashCode")
    void mismoIdMismoHashCode() {
        Metrica a = Fabrica.conId(Fabrica.metricaCobertura(Fabrica.proyecto()), 42L);
        Metrica b = Fabrica.conId(Fabrica.metricaCobertura(Fabrica.proyecto()), 42L);

        assertEquals(a.hashCode(), b.hashCode());
    }

    // -------------------------------------------------------------------------
    // Escenario del issue: Map<Metrica, List<Medicion>> con instancia desacoplada
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Una instancia desacoplada resuelve sus mediciones en el mapa por id (fix CU05/CU06)")
    void instanciaDesacopladaEncuentraMedicionesEnMapa() {
        Proyecto proyecto = Fabrica.conId(Fabrica.proyecto(), 1L);

        // Instancia «original» (simula la cargada por Hibernate en la sesión)
        Metrica metricaOriginal = Fabrica.conId(Fabrica.metricaCobertura(proyecto), 10L);

        // Medición que apunta a metricaOriginal
        Medicion m1 = metricaOriginal.registrarMedicion(85, "Sprint 1", "dev", Fabrica.AHORA);
        Medicion m2 = metricaOriginal.registrarMedicion(70, "Sprint 2", "dev", Fabrica.AHORA.plusDays(7));

        // El mapa se construye agrupando por Medicion::getMetrica (como en LectorSaludProyecto)
        Map<Metrica, List<Medicion>> porMetrica = List.of(m1, m2).stream()
                .collect(Collectors.groupingBy(Medicion::getMetrica, LinkedHashMap::new, Collectors.toList()));

        // Instancia «desacoplada»: distinto objeto Java, mismo id
        Metrica metricaDesacoplada = Fabrica.conId(Fabrica.metricaCobertura(proyecto), 10L);

        List<Medicion> encontradas = porMetrica.getOrDefault(metricaDesacoplada, List.of());

        assertEquals(2, encontradas.size(),
                "Una instancia desacoplada con el mismo id debe resolver sus mediciones en el mapa");
    }
}
