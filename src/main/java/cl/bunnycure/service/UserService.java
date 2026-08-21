package cl.bunnycure.service;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class UserService implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        
        cl.bunnycure.domain.enums.Role roleEnum = user.getRoleEnum();
        List<String> rolesList = switch (roleEnum) {
            case SUPER_ADMIN -> List.of("SUPER_ADMIN", "SALON_ADMIN", "ADMIN");
            case SALON_ADMIN, ADMIN -> List.of("SALON_ADMIN", "ADMIN");
            case RECEPTIONIST -> List.of("RECEPTIONIST");
            case SPECIALIST, STAFF -> List.of("SPECIALIST", "STAFF");
        };

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .roles(rolesList.toArray(new String[0]))
                .build();
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Transactional
    public User createUser(String username, String password, String fullName, String email) {
        return createUser(username, password, fullName, email, "SPECIALIST");
    }

    @Transactional
    public User createUser(String username, String password, String fullName, String email, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("El usuario ya existe");
        }

        String assignedRole = role != null && !role.isBlank() 
                ? cl.bunnycure.domain.enums.Role.fromString(role).name() 
                : "SPECIALIST";

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .email(email)
                .enabled(true)
                .role(assignedRole)
                .passwordChangeRequired(true) // Por defecto requiere cambio
                .build();

        User saved = userRepository.save(user);
        log.info("[USER] Usuario creado: {} con rol: {}", username, assignedRole);
        return saved;
    }

    @Transactional
    public User updateUser(Long id, String fullName, String email) {
        return updateUser(id, fullName, email, null, null);
    }

    @Transactional
    public User updateUser(Long id, String fullName, String email, String role, Boolean enabled) {
        User user = findById(id);
        if (fullName != null) {
            user.setFullName(fullName);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (role != null && !role.isBlank()) {
            user.setRole(cl.bunnycure.domain.enums.Role.fromString(role).name());
        }
        if (enabled != null) {
            user.setEnabled(enabled);
        }
        
        User updated = userRepository.save(user);
        log.info("[USER] Usuario actualizado: {}", user.getUsername());
        return updated;
    }

    @Transactional
    public void changePassword(Long id, String newPassword) {
        User user = findById(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("[USER] Contraseña actualizada para: {}", user.getUsername());
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = findByUsername(username);
        
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Contraseña actual incorrecta");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false); // Ya cambió la contraseña
        userRepository.save(user);
        log.info("[USER] Contraseña actualizada para: {}", username);
    }

    @Transactional
    public void markPasswordChangeRequired(Long userId) {
        User user = findById(userId);
        user.setPasswordChangeRequired(true);
        userRepository.save(user);
        log.info("[USER] Marcado cambio de contraseña requerido para: {}", user.getUsername());
    }

    @Transactional
    public void toggleEnabled(Long id) {
        User user = findById(id);
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        log.info("[USER] Usuario {} {}", user.getUsername(), user.isEnabled() ? "habilitado" : "deshabilitado");
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = findById(id);
        if (userRepository.count() == 1) {
            throw new RuntimeException("No se puede eliminar el único usuario administrador");
        }
        userRepository.deleteById(id);
        log.info("[USER] Usuario eliminado: {}", user.getUsername());
    }
}
