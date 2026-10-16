package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuración clave/valor de cada salón (nombre, horarios de reserva, plantillas, WhatsApp...).
 *
 * La clave es única por salón (tenant_id, setting_key), no global. El @Id es un sustituto
 * numérico a propósito: si la clave fuese el @Id, Hibernate actualizaría con
 * "WHERE setting_key = ?" y un cambio en un salón sobrescribiría la misma clave en todos.
 */
@Entity
@Table(name = "app_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_settings_tenant_key", columnNames = {"tenant_id", "setting_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "setting_key", length = 100, nullable = false)
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    @Column(length = 200)
    private String description;

    public AppSettings(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
    }
}
