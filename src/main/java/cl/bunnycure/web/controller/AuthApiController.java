package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.service.UserService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.LoginRequest;
import cl.bunnycure.web.dto.LoginResponse;
import cl.bunnycure.web.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller para autenticación.
 * Expone endpoints para login, logout y obtener información del usuario autenticado.
 */
@Slf4j
@Tag(name = "Authentication", description = "API para autenticación y sesión")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @Operation(
            summary = "Login de usuario",
            description = "Autentica un usuario y crea una sesión. Retorna información del usuario autenticado.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Login exitoso",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Credenciales inválidas"
            )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        try {
            log.info("[API] Intento de login para usuario: {}", loginRequest.getUsername());
            
            // Crear token de autenticación
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(), 
                    loginRequest.getPassword()
                );
            
            // Autenticar
            Authentication authentication = authenticationManager.authenticate(authToken);
            
            // Establecer contexto de seguridad
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            
            // IMPORTANTE: Crear sesión HTTP ANTES de guardar el contexto
            // Esto asegura que Spring Security envíe la cookie JSESSIONID
            HttpSession session = request.getSession(true);
            
            // Guardar contexto en sesión
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
                securityContext
            );
            
            // CRÍTICO: Forzar que se envíe la cookie en la respuesta
            // Agregar header Set-Cookie explícitamente si es necesario
            if (session.isNew()) {
                log.info("[API] Nueva sesión creada: {}", session.getId());
            }
            
            // Obtener información del usuario
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            UserDto userDto = UserDto.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .enabled(user.isEnabled())
                    .build();
            
            // Verificar si requiere cambio de contraseña
            // TODO: Implementar campo passwordChangeRequired en User cuando se implemente la funcionalidad
            boolean requiresPasswordChange = false; // user.isPasswordChangeRequired();
            
            LoginResponse loginResponse = LoginResponse.builder()
                    .user(userDto)
                    .requiresPasswordChange(requiresPasswordChange)
                    .message(requiresPasswordChange ? 
                        "Debe cambiar su contraseña" : 
                        "Login exitoso")
                    .build();
            
            log.info("[API] Login exitoso para usuario: {} - Session ID: {}", 
                username, session.getId());
            
            return ResponseEntity.ok(ApiResponse.success(loginResponse));
            
        } catch (BadCredentialsException e) {
            log.warn("[API] Login fallido para usuario: {} - Credenciales inválidas", 
                loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Credenciales inválidas", "INVALID_CREDENTIALS"));
        } catch (Exception e) {
            log.error("[API] Error durante login para usuario: {}", 
                loginRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error durante el login", "LOGIN_ERROR"));
        }
    }

    @Operation(
            summary = "Logout de usuario",
            description = "Cierra la sesión del usuario autenticado.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Logout exitoso"
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null) {
            log.info("[API] Logout para usuario: {}", authentication.getName());
        }
        
        // Limpiar contexto de seguridad
        SecurityContextHolder.clearContext();
        
        // Invalidar sesión
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        
        log.info("[API] Logout exitoso");
        
        return ResponseEntity.ok(ApiResponse.success("Logout exitoso"));
    }

    @Operation(
            summary = "Obtener usuario actual",
            description = "Obtiene la información del usuario actualmente autenticado.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Usuario autenticado",
                    content = @Content(schema = @Schema(implementation = UserDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "No autenticado"
            )
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No autenticado", "NOT_AUTHENTICATED"));
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username);
        
        UserDto dto = UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }
}
