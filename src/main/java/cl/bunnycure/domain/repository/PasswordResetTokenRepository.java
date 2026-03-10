package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Query("""
        SELECT t
        FROM PasswordResetToken t
        JOIN FETCH t.user
        WHERE t.token = :token
          AND t.used = false
          AND t.expiresAt > :now
    """)
    Optional<PasswordResetToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        UPDATE PasswordResetToken t
        SET t.used = true
        WHERE t.user.id = :userId
          AND t.used = false
    """)
    int markAllActiveAsUsedByUserId(@Param("userId") Long userId);
}
