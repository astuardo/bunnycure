package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "marketing_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_marketing_templates_tenant_name", columnNames = {"tenant_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketingTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(length = 150)
    private String occasion;

    @Column(length = 20)
    private String emoji;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = "MARKETING";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String language = "es_CL";

    @Column(name = "header_text", length = 255)
    private String headerText;

    @Column(name = "body_text", nullable = false, columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "footer_text", length = 100)
    private String footerText;

    @Column(name = "button_text", length = 100)
    private String buttonText;

    @Column(name = "button_url", length = 255)
    private String buttonUrl;

    @Column(name = "sample_variables", columnDefinition = "TEXT")
    private String sampleVariables;

    @Column(name = "meta_status", nullable = false, length = 50)
    @Builder.Default
    private String metaStatus = "NOT_REGISTERED";

    @Column(name = "meta_id", length = 100)
    private String metaId;

    @Column(length = 50)
    @Builder.Default
    private String source = "AI_AGENT";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
