package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.exception.GlobalExceptionHandler;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.service.UserService;
import cl.bunnycure.web.dto.ChangePasswordRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserApiControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserApiController userApiController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userApiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void changePassword_Success() throws Exception {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");

        when(userService.findById(1L)).thenReturn(user);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass123!")
                .newPassword("NewPass123!")
                .confirmPassword("NewPass123!")
                .build();

        // Act & Assert
        mockMvc.perform(put("/api/users/1/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Contraseña actualizada exitosamente"));

        verify(userService).changePassword(1L, "NewPass123!");
    }

    @Test
    void changePassword_ReturnsBadRequestWhenPasswordsDoNotMatch() throws Exception {
        // Arrange
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass123!")
                .newPassword("NewPass123!")
                .confirmPassword("DifferentPass123!")
                .build();

        // Act & Assert
        mockMvc.perform(put("/api/users/1/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("La nueva contraseña y la confirmación no coinciden"));

        verify(userService, never()).changePassword(anyLong(), anyString());
    }

    @Test
    void changePassword_ReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        // Arrange
        when(userService.findById(99L)).thenThrow(new ResourceNotFoundException("Usuario no encontrado"));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass123!")
                .newPassword("NewPass123!")
                .confirmPassword("NewPass123!")
                .build();

        // Act & Assert
        mockMvc.perform(put("/api/users/99/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        verify(userService, never()).changePassword(eq(99L), anyString());
    }
}
