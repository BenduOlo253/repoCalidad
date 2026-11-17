package mx.uaz.sqa.dominio.modelo;

import java.util.Objects;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Superclase de todas las entidades persistentes: concentra la identidad.
 *
 * <p>equals() y hashCode() se basan en el {@code id} de la base de datos para que
 * instancias desacopladas de la sesión de Hibernate (detached) con el mismo
 * identificador sean tratadas como el mismo objeto en colecciones y mapas
 * (CU05, CU06 / RS-09, RS-10).</p>
 *
 * <p>Cuando el {@code id} aún es {@code null} (entidad nueva, no persistida)
 * se delega a la identidad de objeto para mantener consistencia dentro de
 * la misma sesión antes de la inserción.</p>
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

    /**
     * Dos entidades son iguales si son del mismo tipo concreto y tienen el mismo {@code id}.
     * Las entidades sin {@code id} (nuevas) solo son iguales a sí mismas.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EntidadPersistente otra = (EntidadPersistente) obj;
        if (this.id == null) return false;
        return Objects.equals(this.id, otra.id);
    }

    /**
     * Hashcode estable: usa el {@code id} cuando existe, o 0 para entidades nuevas.
     * El valor 0 para entidades sin id es seguro porque equals() también devuelve
     * {@code false} para ese caso (excepto {@code this == obj}).
     */
    @Override
    public int hashCode() {
        return id == null ? 0 : Long.hashCode(id);
    }
}
