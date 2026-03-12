package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_service_records")
@Getter
@Setter
@NoArgsConstructor
public class CustomerServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_service_records_seq_generator")
    @SequenceGenerator(
            name = "customer_service_records_seq_generator",
            sequenceName = "customer_service_records_seq",
            allocationSize = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "source_message_id", nullable = false, unique = true, length = 120)
    private String sourceMessageId;

    @Column(name = "whatsapp_media_id", nullable = false, unique = true, length = 120)
    private String whatsappMediaId;

    @Column(name = "source_from_phone", nullable = false, length = 20)
    private String sourceFromPhone;

    @Column(name = "client_phone_in_payload", nullable = false, length = 20)
    private String clientPhoneInPayload;

    @Column(name = "service_detail", nullable = false, length = 500)
    private String serviceDetail;

    @Column(name = "photo_caption", length = 1000)
    private String photoCaption;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "media_sha256", length = 120)
    private String mediaSha256;

    @Column(name = "photo_data", columnDefinition = "bytea")
    private byte[] photoData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
