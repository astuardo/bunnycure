package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "service_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "service_catalog_compatibility",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "compatible_service_id")
    )
    @Builder.Default
    private Set<ServiceCatalog> compatibleServices = new LinkedHashSet<>();

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    @Transient
    public String getDurationFormatted() {
        int h = durationMinutes / 60;
        int m = durationMinutes % 60;
        if (m == 0) return h + " hr" + (h > 1 ? "s" : "");
        return h > 0 ? h + "h " + m + "min" : m + " min";
    }
}
