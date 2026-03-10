package cl.bunnycure.service;

import cl.bunnycure.domain.model.PasswordResetToken;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.PasswordResetTokenRepository;
import cl.bunnycure.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, mailSender);
        ReflectionTestUtils.setField(passwordResetService, "mailEnabled", false);
        ReflectionTestUtils.setField(passwordResetService, "mailFrom", "noreply@bunnycure.cl");
    }

    @Test
    void requestReset_shouldCreateTokenForExistingUser() {
        User user = User.builder().id(10L).email("admin@bunnycure.cl").username("admin").build();
        when(userRepository.findByEmailIgnoreCase("admin@bunnycure.cl")).thenReturn(Optional.of(user));

        passwordResetService.requestReset("admin@bunnycure.cl", "https://admin.bunnycure.cl");

        verify(tokenRepository).markAllActiveAsUsedByUserId(10L);
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_shouldEncodeAndPersistPassword() {
        User user = User.builder().id(7L).username("admin").password("old").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .token("abc")
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .used(false)
                .build();

        when(tokenRepository.findValidToken(eq("abc"), any(LocalDateTime.class))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encoded-pass");

        passwordResetService.resetPassword("abc", "newPassword123");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("encoded-pass", userCaptor.getValue().getPassword());
    }

    @Test
    void resetPassword_shouldFailWhenTokenInvalid() {
        when(tokenRepository.findValidToken(eq("missing"), any(LocalDateTime.class))).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> passwordResetService.resetPassword("missing", "newPassword123"));
    }
}
