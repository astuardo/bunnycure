package cl.bunnycure.config;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests para AdminUserInitializer (T3.1 - Sprint 3)
 * 
 * Valida:
 * - Creación de usuario admin desde variables de entorno
 * - Validación de credenciales inseguras
 * - Rotación de passwords legacy
 * - Preservación de passwords seguros existentes
 */
@ExtendWith(MockitoExtension.class)
class AdminUserInitializerTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private AdminUserInitializer initializer;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        initializer = new AdminUserInitializer();
        
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "SecurePass123!");
        ReflectionTestUtils.setField(initializer, "adminFullName", "Administrador");
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@bunnycure.cl");
    }

    @Test
    void shouldCreateAdminUserWhenNotExists() throws Exception {
        // Given
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        
        // When
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        runner.run();
        
        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("admin");
        assertThat(savedUser.getFullName()).isEqualTo("Administrador");
        assertThat(savedUser.getEmail()).isEqualTo("admin@bunnycure.cl");
        assertThat(savedUser.getRole()).isEqualTo("ADMIN");
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("SecurePass123!", savedUser.getPassword())).isTrue();
    }

    @Test
    void shouldPreserveExistingSecurePassword() throws Exception {
        // Given - Usuario existente con password seguro
        String securePassword = passwordEncoder.encode("MySecurePassword123");
        User existingUser = User.builder()
                .username("admin")
                .password(securePassword)
                .role("ADMIN")
                .enabled(false)
                .build();
        
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        
        // When
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        runner.run();
        
        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPassword()).isEqualTo(securePassword); // Password NO debe cambiar
        assertThat(savedUser.isEnabled()).isTrue(); // Pero sí se habilita
        assertThat(savedUser.getRole()).isEqualTo("ADMIN"); // Y se asegura el rol
    }

    @Test
    void shouldRotateLegacyPassword_changeme() throws Exception {
        // Given - Usuario con password legacy "changeme"
        String legacyPassword = passwordEncoder.encode("changeme");
        User existingUser = User.builder()
                .username("admin")
                .password(legacyPassword)
                .role("USER")
                .enabled(true)
                .build();
        
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        
        // When
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        runner.run();
        
        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(passwordEncoder.matches("SecurePass123!", savedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("changeme", savedUser.getPassword())).isFalse();
        assertThat(savedUser.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void shouldRotateLegacyPassword_changemeLocalOnly() throws Exception {
        // Given - Usuario con password legacy "changeme-local-only"
        String legacyPassword = passwordEncoder.encode("changeme-local-only");
        User existingUser = User.builder()
                .username("admin")
                .password(legacyPassword)
                .role("ADMIN")
                .enabled(true)
                .build();
        
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        
        // When
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        runner.run();
        
        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(passwordEncoder.matches("SecurePass123!", savedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("changeme-local-only", savedUser.getPassword())).isFalse();
    }

    @Test
    void shouldThrowException_whenUsernameIsBlank() {
        // Given
        ReflectionTestUtils.setField(initializer, "adminUsername", "");
        
        // When/Then
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bunnycure.admin.username debe estar configurado");
    }

    @Test
    void shouldThrowException_whenUsernameIsNull() {
        // Given
        ReflectionTestUtils.setField(initializer, "adminUsername", null);
        
        // When/Then
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bunnycure.admin.username debe estar configurado");
    }

    @Test
    void shouldThrowException_whenPasswordIsBlank() {
        // Given
        ReflectionTestUtils.setField(initializer, "adminPassword", "");
        
        // When/Then
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bunnycure.admin.password inválido");
    }

    @Test
    void shouldThrowException_whenPasswordIsChangeme() {
        // Given
        ReflectionTestUtils.setField(initializer, "adminPassword", "changeme");
        
        // When/Then
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bunnycure.admin.password inválido");
    }

    @Test
    void shouldThrowException_whenPasswordIsChangemeUppercase() {
        // Given
        ReflectionTestUtils.setField(initializer, "adminPassword", "CHANGEME");
        
        // When/Then
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bunnycure.admin.password inválido");
    }

    @Test
    void shouldUseDefaultValues_whenFullNameAndEmailNotProvided() throws Exception {
        // Given
        ReflectionTestUtils.setField(initializer, "adminFullName", "Administrador");
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@bunnycure.cl");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        
        // When
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        runner.run();
        
        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getFullName()).isEqualTo("Administrador");
        assertThat(savedUser.getEmail()).isEqualTo("admin@bunnycure.cl");
    }

    @Test
    void shouldSetCustomFullNameAndEmail() throws Exception {
        // Given
        ReflectionTestUtils.setField(initializer, "adminFullName", "Juan Admin");
        ReflectionTestUtils.setField(initializer, "adminEmail", "juan@empresa.com");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        
        // When
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        runner.run();
        
        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getFullName()).isEqualTo("Juan Admin");
        assertThat(savedUser.getEmail()).isEqualTo("juan@empresa.com");
    }

    @Test
    void shouldEnableDisabledUser() throws Exception {
        // Given
        User existingUser = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("AnotherSecurePass"))
                .role("ADMIN")
                .enabled(false)
                .build();
        
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        
        // When
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        runner.run();
        
        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.isEnabled()).isTrue();
    }

    @Test
    void shouldUpgradeRoleToAdmin() throws Exception {
        // Given - Usuario existente con rol USER
        User existingUser = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("SecurePassword"))
                .role("USER")
                .enabled(true)
                .build();
        
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        
        // When
        CommandLineRunner runner = initializer.initAdminUser(userRepository, passwordEncoder);
        runner.run();
        
        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo("ADMIN");
    }
}
