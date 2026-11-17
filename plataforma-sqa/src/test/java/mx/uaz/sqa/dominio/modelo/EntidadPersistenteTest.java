package mx.uaz.sqa.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mx.uaz.sqa.soporte.Fabrica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EntidadPersistente – Identidad y control de concurrencia optimista")
class EntidadPersistenteTest {

    @Test
    @DisplayName("Una entidad nueva no tiene id ni versión asignados y esNueva devuelve true")
    void entidadNueva() {
        Proyecto proyecto = Fabrica.proyecto();

        assertTrue(proyecto.esNueva());
        assertNull(proyecto.getId());
        assertNull(proyecto.getVersion());
    }

    @Test
    @DisplayName("Una entidad con id y versión asignados refleja sus valores y esNueva devuelve false")
    void entidadPersistida() {
        Proyecto proyecto = Fabrica.conId(Fabrica.proyecto(), 42L);
        Fabrica.conVersion(proyecto, 3L);

        assertFalse(proyecto.esNueva());
        assertEquals(42L, proyecto.getId());
        assertEquals(3L, proyecto.getVersion());
    }
}
