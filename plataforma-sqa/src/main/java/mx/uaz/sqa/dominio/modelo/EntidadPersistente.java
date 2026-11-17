package mx.uaz.sqa.dominio.modelo;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Superclase de todas las entidades persistentes: concentra la identidad y
 * el control de concurrencia optimista (RNF-05).
 */
@MappedSuperclass
public abstract class EntidadPersistente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public boolean esNueva() {
        return id == null;
    }
}
