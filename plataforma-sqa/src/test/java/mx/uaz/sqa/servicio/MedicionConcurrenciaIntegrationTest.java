package mx.uaz.sqa.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mx.uaz.sqa.dominio.modelo.EstadoSemaforo;
import mx.uaz.sqa.dominio.modelo.Frecuencia;
import mx.uaz.sqa.dominio.modelo.Medicion;
import mx.uaz.sqa.dominio.modelo.Metrica;
import mx.uaz.sqa.dominio.modelo.Proyecto;
import mx.uaz.sqa.dominio.modelo.SentidoMetrica;
import mx.uaz.sqa.dominio.modelo.UnidadMedida;
import mx.uaz.sqa.persistencia.MedicionRepository;
import mx.uaz.sqa.persistencia.MetricaRepository;
import mx.uaz.sqa.servicio.dto.DatosMetrica;
import mx.uaz.sqa.servicio.dto.DatosProyecto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CU03 / RS-04, RNF-05: Verificación de control de concurrencia optimista (@Version).
 *
 * <p>Verifica que dos transacciones concurrentes sobre la misma métrica no provoquen
 * sobreescrituras silenciosas (Lost Updates) y que Hibernate detecte el conflicto
 * abortando la transacción desfasada mediante OptimisticLockingFailureException.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CU03 / RNF-05 – Control de concurrencia optimista en mediciones")
class MedicionConcurrenciaIntegrationTest {

    @Autowired
    private ProyectoService proyectoService;

    @Autowired
    private MetricaService metricaService;

    @Autowired
    private MetricaRepository metricaRepository;

    @Autowired
    private MedicionRepository medicionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock reloj;

    @Test
    @DisplayName("Dos transacciones concurrentes: la segunda detecta el conflicto mediante @Version y se aborta")
    void detectaConflictoConcurrenciaOptimista() throws Exception {
        // 1. Preparar proyecto y métrica
        Proyecto proyecto = proyectoService.crear(new DatosProyecto(
                "Proyecto Concurrencia RNF-05",
                "Prueba de concurrencia",
                "Java",
                "Ago-Dic 2026",
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 12, 11)
        ));

        Metrica metrica = metricaService.configurar(proyecto.getId(), new DatosMetrica(
                "Cobertura Concurrente",
                UnidadMedida.PORCENTAJE,
                Frecuencia.POR_SPRINT,
                SentidoMetrica.MAYOR_ES_MEJOR,
                80.0,
                60.0
        ));

        Long metricaId = metrica.getId();
        assertNotNull(metrica.getVersion(), "La métrica persistida debe tener número de versión inicial");

        // Latches para sincronizar los hilos A y B
        CountDownLatch ambosCargados = new CountDownLatch(2);
        CountDownLatch hiloACompletado = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Hilo A: carga la métrica, espera a que el Hilo B también la cargue, registra 70.0 y hace commit
        Future<?> futuroHiloA = executor.submit(() -> {
            TransactionTemplate txA = new TransactionTemplate(transactionManager);
            txA.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return txA.execute(status -> {
                Metrica mA = metricaRepository.findById(metricaId).orElseThrow();
                ambosCargados.countDown();
                try {
                    assertTrue(ambosCargados.await(5, TimeUnit.SECONDS), "Timeout esperando sincronización");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }

                Medicion medicionA = mA.registrarMedicion(70.0, "Hilo A", "dev1", LocalDateTime.now(reloj));
                medicionRepository.save(medicionA);
                return mA;
            });
        });

        // Hilo B: carga la misma métrica (con la misma versión), espera a que el Hilo A haga commit, registra 85.0 e intenta commit
        Future<?> futuroHiloB = executor.submit(() -> {
            TransactionTemplate txB = new TransactionTemplate(transactionManager);
            txB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return txB.execute(status -> {
                Metrica mB = metricaRepository.findById(metricaId).orElseThrow();
                ambosCargados.countDown();
                try {
                    assertTrue(ambosCargados.await(5, TimeUnit.SECONDS), "Timeout esperando sincronización");
                    assertTrue(hiloACompletado.await(5, TimeUnit.SECONDS), "Timeout esperando fin de hilo A");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }

                Medicion medicionB = mB.registrarMedicion(85.0, "Hilo B", "dev2", LocalDateTime.now(reloj));
                medicionRepository.save(medicionB);
                return mB;
            });
        });

        // El hilo A debe terminar exitosamente
        futuroHiloA.get(5, TimeUnit.SECONDS);
        hiloACompletado.countDown();

        // El hilo B debe fallar al hacer commit por conflicto de versión (@Version)
        ExecutionException excepcionB = assertThrows(ExecutionException.class, () -> futuroHiloB.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OptimisticLockingFailureException.class, excepcionB.getCause(),
                "Debe lanzar OptimisticLockingFailureException al detectar el conflicto de versión");

        executor.shutdown();

        // 3. Verificar estado en base de datos: no hubo Lost Update, se mantuvo el valor del hilo A
        Metrica metricaFinal = metricaRepository.findById(metricaId).orElseThrow();
        assertEquals(70.0, metricaFinal.getUltimoValor(), "El último valor debe corresponder a la transacción que ganó");
        assertEquals(EstadoSemaforo.AMARILLO, metricaFinal.getEstadoActual());
        assertEquals(1, medicionRepository.findByMetricaIdOrderByRegistradoEnAscIdAsc(metricaId).size(),
                "Solo debe haberse persistido la medición de la transacción exitosa");
    }
}
