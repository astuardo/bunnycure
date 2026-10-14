package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findById(@org.springframework.data.repository.query.Param("id") Long id);
    
    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);
    
    boolean existsByUsername(String username);
}
