package cl.bunnycure.service;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .roles(user.getRole())
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
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("El usuario ya existe");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .email(email)
                .enabled(true)
                .role("ADMIN")
                .build();

        User saved = userRepository.save(user);
        log.info("[USER] Usuario creado: {}", username);
        return saved;
    }

    @Transactional
    public User updateUser(Long id, String fullName, String email) {
        User user = findById(id);
        user.setFullName(fullName);
        user.setEmail(email);
        
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
        userRepository.save(user);
        log.info("[USER] Contraseña actualizada para: {}", username);
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
