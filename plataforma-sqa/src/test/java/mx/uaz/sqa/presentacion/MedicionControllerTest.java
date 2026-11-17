package mx.uaz.sqa.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import mx.uaz.sqa.dominio.modelo.Metrica;
import mx.uaz.sqa.dominio.modelo.Proyecto;
import mx.uaz.sqa.presentacion.formulario.MedicionForm;
import mx.uaz.sqa.servicio.MedicionService;
import mx.uaz.sqa.servicio.MetricaService;
import mx.uaz.sqa.soporte.Fabrica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicionController – Registro de mediciones y manejo de concurrencia")
class MedicionControllerTest {

    @Mock
    private MetricaService metricaService;

    @Mock
    private MedicionService medicionService;

    private MedicionController controller;
    private Metrica metrica;

    @BeforeEach
    void setUp() {
        controller = new MedicionController(metricaService, medicionService);
        Proyecto proyecto = Fabrica.conId(Fabrica.proyecto(), 1L);
        metrica = Fabrica.conId(Fabrica.metricaCobertura(proyecto), 10L);
    }

    @Test
    @DisplayName("CU03 / RNF-05: Conflicto de bloqueo optimista muestra error de concurrencia en el formulario")
    void manejaConflictoDeBloqueoOptimista() {
        when(metricaService.obtener(10L)).thenReturn(metrica);
        when(medicionService.historial(10L)).thenReturn(List.of());
        when(medicionService.registrar(10L, "85.0", null, "dev"))
                .thenThrow(new OptimisticLockingFailureException("Conflicto de concurrencia"));

        MedicionForm form = new MedicionForm();
        form.setValor("85.0");
        BindingResult resultado = new BeanPropertyBindingResult(form, "form");
        Principal principal = () -> "dev";
        Model model = new ConcurrentModel();
        RedirectAttributesModelMap atributos = new RedirectAttributesModelMap();

        String vista = controller.registrar(10L, form, resultado, principal, model, atributos);

        assertEquals("metricas/mediciones", vista);
        assertTrue(resultado.hasGlobalErrors());
        assertEquals("concurrencia", resultado.getGlobalError().getCode());
    }
}
