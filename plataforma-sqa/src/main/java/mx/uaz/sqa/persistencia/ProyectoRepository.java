package mx.uaz.sqa.persistencia;

import java.util.List;
import mx.uaz.sqa.dominio.modelo.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

    boolean existsByNombreIgnoreCaseAndPeriodoEtiquetaIgnoreCase(String nombre, String etiqueta);

    boolean existsByNombreIgnoreCaseAndPeriodoEtiquetaIgnoreCaseAndIdNot(String nombre, String etiqueta, Long id);

    // Issue #3: Obtener proyectos del mismo periodo para validar consistencia de rangos temporales
    List<Proyecto> findByPeriodoEtiquetaIgnoreCase(String etiqueta);
}