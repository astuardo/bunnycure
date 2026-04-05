package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.service.UserService;
import cl.bunnycure.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API Controller para gestión de usuarios.
 * Solo accesible para usuarios con rol ADMIN (configurado en SecurityConfig).
 */
@Slf4j
@Tag(name = "Users", description = "API para gestión de usuarios (solo ADMIN)")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;

    @Operation(
            summary = "Listar todos los usuarios",
            description = "Obtiene la lista completa de usuarios del sistema.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de usuarios obtenida exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = UserDto.class))
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> listAll() {
        List<User> users = userService.findAll();
        
        List<UserDto> dtos = users.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        
        log.info("[API] Listando {} usuarios", dtos.size());
        
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(
            summary = "Obtener usuario por ID",
            description = "Obtiene los detalles de un usuario específico.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Usuario encontrado",
                    content = @Content(schema = @Schema(implementation = UserDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getById(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable Long id) {
        
        try {
            User user = userService.findById(id);
            UserDto dto = toDto(user);
            
            log.info("[API] Obteniendo usuario: {}", user.getUsername());
            
            return ResponseEntity.ok(ApiResponse.success(dto));
        } catch (RuntimeException e) {
            log.error("[API] Error al obtener usuario con ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Usuario no encontrado", "USER_NOT_FOUND"));
        }
    }

    @Operation(
            summary = "Crear nuevo usuario",
            description = "Crea un nuevo usuario en el sistema con rol ADMIN.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Usuario creado exitosamente",
                    content = @Content(schema = @Schema(implementation = UserDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos o usuario duplicado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> create(
            @Valid @RequestBody CreateUserRequest request) {
        
        try {
            log.info("[API] Creando usuario: {}", request.getUsername());
            
            User created = userService.createUser(
                    request.getUsername(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getEmail()
            );
            
            UserDto dto = toDto(created);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(dto));
                    
        } catch (RuntimeException e) {
            log.error("[API] Error al crear usuario {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), "USER_CREATION_ERROR"));
        }
    }

    @Operation(
            summary = "Actualizar usuario",
            description = "Actualiza los datos de un usuario existente (nombre completo y email).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Usuario actualizado exitosamente",
                    content = @Content(schema = @Schema(implementation = UserDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> update(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable Long id,
            
            @Valid @RequestBody UpdateUserRequest request) {
        
        try {
            log.info("[API] Actualizando usuario con ID: {}", id);
            
            User updated = userService.updateUser(
                    id,
                    request.getFullName(),
                    request.getEmail()
            );
            
            UserDto dto = toDto(updated);
            
            return ResponseEntity.ok(ApiResponse.success(dto));
            
        } catch (RuntimeException e) {
            log.error("[API] Error al actualizar usuario {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Usuario no encontrado", "USER_NOT_FOUND"));
        }
    }

    @Operation(
            summary = "Eliminar usuario",
            description = """
                    Elimina permanentemente un usuario del sistema.
                    No se permite eliminar el propio usuario ni el último usuario activo.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Usuario eliminado exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "No se puede eliminar el usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable Long id) {
        
        try {
            // Obtener el usuario autenticado actual
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUsername = auth.getName();
            
            // Obtener el usuario a eliminar
            User userToDelete = userService.findById(id);
            
            // Verificar que no se elimine a sí mismo
            if (userToDelete.getUsername().equals(currentUsername)) {
                log.warn("[API] Usuario {} intentó eliminarse a sí mismo", currentUsername);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(
                                "No puede eliminar su propio usuario",
                                "CANNOT_DELETE_SELF"
                        ));
            }
            
            log.info("[API] Eliminando usuario: {}", userToDelete.getUsername());
            
            userService.deleteUser(id);
            
            return ResponseEntity.noContent().build();
            
        } catch (RuntimeException e) {
            log.error("[API] Error al eliminar usuario {}: {}", id, e.getMessage());
            
            if (e.getMessage().contains("único usuario")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(e.getMessage(), "CANNOT_DELETE_LAST_USER"));
            }
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Usuario no encontrado", "USER_NOT_FOUND"));
        }
    }

    @Operation(
            summary = "Activar/Desactivar usuario",
            description = """
                    Cambia el estado de habilitación del usuario.
                    No se permite desactivar el último usuario activo.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Estado del usuario actualizado",
                    content = @Content(schema = @Schema(implementation = UserDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "No se puede desactivar el último usuario activo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @PutMapping("/{id}/toggle-enabled")
    public ResponseEntity<ApiResponse<UserDto>> toggleEnabled(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable Long id) {
        
        try {
            User user = userService.findById(id);
            
            // Si el usuario está activo y se va a desactivar, verificar que no sea el último
            if (user.isEnabled()) {
                long activeUsers = userService.findAll().stream()
                        .filter(User::isEnabled)
                        .count();
                
                if (activeUsers == 1) {
                    log.warn("[API] Intento de desactivar el último usuario activo: {}", user.getUsername());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error(
                                    "No se puede desactivar el último usuario activo",
                                    "CANNOT_DISABLE_LAST_ACTIVE_USER"
                            ));
                }
            }
            
            log.info("[API] Cambiando estado de usuario: {}", user.getUsername());
            
            userService.toggleEnabled(id);
            
            // Recargar el usuario con el nuevo estado
            User updated = userService.findById(id);
            UserDto dto = toDto(updated);
            
            return ResponseEntity.ok(ApiResponse.success(dto));
            
        } catch (RuntimeException e) {
            log.error("[API] Error al cambiar estado del usuario {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Usuario no encontrado", "USER_NOT_FOUND"));
        }
    }

    @Operation(
            summary = "Cambiar contraseña de usuario",
            description = "Permite a un administrador cambiar la contraseña de cualquier usuario.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Contraseña actualizada exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @PutMapping("/{id}/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable Long id,
            
            @Valid @RequestBody ChangePasswordRequest request) {
        
        try {
            User user = userService.findById(id);
            
            log.info("[API] Cambiando contraseña del usuario: {}", user.getUsername());
            
            userService.changePassword(id, request.getNewPassword());
            
            return ResponseEntity.ok(ApiResponse.success("Contraseña actualizada exitosamente"));
            
        } catch (RuntimeException e) {
            log.error("[API] Error al cambiar contraseña del usuario {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Usuario no encontrado", "USER_NOT_FOUND"));
        }
    }

    /**
     * Convierte una entidad User a UserDto (sin contraseña).
     */
    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }
}
