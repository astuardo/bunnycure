package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.GiftCardStatus;
import cl.bunnycure.domain.model.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    Optional<GiftCard> findByCode(String code);

    @Query("""
        SELECT DISTINCT gc FROM GiftCard gc
        LEFT JOIN FETCH gc.items items
        LEFT JOIN FETCH items.service
        LEFT JOIN FETCH gc.beneficiaryCustomer
        WHERE gc.id = :id
    """)
    Optional<GiftCard> findByIdWithDetails(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT gc FROM GiftCard gc
        LEFT JOIN FETCH gc.items items
        LEFT JOIN FETCH items.service
        LEFT JOIN FETCH gc.beneficiaryCustomer
        WHERE gc.code = :code
    """)
    Optional<GiftCard> findByCodeWithDetails(@Param("code") String code);

    @Query("""
        SELECT gc FROM GiftCard gc
        WHERE (:status IS NULL OR gc.status = :status)
          AND (:expiringBefore IS NULL OR gc.expiresOn <= :expiringBefore)
          AND (
            :search IS NULL OR :search = '' OR
            LOWER(gc.code) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(gc.beneficiaryNameSnapshot) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(gc.beneficiaryPhoneSnapshot) LIKE LOWER(CONCAT('%', :search, '%'))
          )
        ORDER BY gc.createdAt DESC
    """)
    List<GiftCard> search(
            @Param("search") String search,
            @Param("status") GiftCardStatus status,
            @Param("expiringBefore") LocalDate expiringBefore
    );
}
