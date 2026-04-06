package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.service.JwtService;
import cl.bunnycure.service.UserService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.ChangePasswordRequest;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller para autenticación.
 * Expone endpoints para login, logout y obtener información del usuario autenticado.
 * Soporta autenticación dual: JWT (móvil) + Session Cookie (desktop).
 */
@Slf4j
@Tag(name = "Authentication", description = "API para autenticación y sesión")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Operation(
            summary = "Login de usuario",
            description = "Autentica un usuario y retorna JWT + información del usuario. También crea sesión HTTP para compatibilidad con desktop.")
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
            log.info("[AUTH-LOGIN] ========== INICIO LOGIN ==========");
            log.info("[AUTH-LOGIN] Usuario: {}", loginRequest.getUsername());
            log.info("[AUTH-LOGIN] Request headers:");
            request.getHeaderNames().asIterator().forEachRemaining(header -> 
                log.info("[AUTH-LOGIN]   {}: {}", header, request.getHeader(header))
            );
            
            // Crear token de autenticación
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(), 
                    loginRequest.getPassword()
                );
            
            // Autenticar
            log.info("[AUTH-LOGIN] Autenticando con AuthenticationManager...");
            Authentication authentication = authenticationManager.authenticate(authToken);
            log.info("[AUTH-LOGIN] ✓ Autenticación exitosa");
            
            // Establecer contexto de seguridad
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            log.info("[AUTH-LOGIN] ✓ Contexto de seguridad establecido");
            
            // IMPORTANTE: Crear sesión HTTP para compatibilidad desktop
            HttpSession session = request.getSession(true);
            log.info("[AUTH-LOGIN] Sesión HTTP: {} (nueva: {})", session.getId(), session.isNew());
            
            // Guardar contexto en sesión
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
                securityContext
            );
            log.info("[AUTH-LOGIN] ✓ Contexto guardado en sesión");
            
            // Obtener información del usuario
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            log.info("[AUTH-LOGIN] Usuario cargado: {} (role: {})", user.getUsername(), user.getRole());
            
            // GENERAR JWT para clientes móviles
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String jwtToken = jwtService.generateToken(userDetails);
            log.info("[AUTH-LOGIN] ✓ JWT generado (longitud: {})", jwtToken.length());
            log.debug("[AUTH-LOGIN] JWT token: {}", jwtToken);
            
            UserDto userDto = UserDto.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .enabled(user.isEnabled())
                    .build();
            
            // Verificar si requiere cambio de contraseña
            boolean requiresPasswordChange = user.isPasswordChangeRequired();
            
            LoginResponse loginResponse = LoginResponse.builder()
                    .user(userDto)
                    .token(jwtToken)  // INCLUIR JWT EN LA RESPUESTA
                    .requiresPasswordChange(requiresPasswordChange)
                    .message(requiresPasswordChange ? 
                        "Debe cambiar su contraseña" : 
                        "Login exitoso")
                    .build();
            
            log.info("[AUTH-LOGIN] ========== LOGIN EXITOSO ==========");
            log.info("[AUTH-LOGIN] Usuario: {}", username);
            log.info("[AUTH-LOGIN] Session ID: {}", session.getId());
            log.info("[AUTH-LOGIN] JWT incluido: SÍ");
            log.info("[AUTH-LOGIN] Response headers que se enviarán:");
            response.getHeaderNames().forEach(header -> 
                log.info("[AUTH-LOGIN]   {}: {}", header, response.getHeader(header))
            );
            log.info("[AUTH-LOGIN] ===========================================");
            
            return ResponseEntity.ok(ApiResponse.success(loginResponse));
            
        } catch (BadCredentialsException e) {
            log.warn("[AUTH-LOGIN] ✗ Login fallido - Credenciales inválidas para: {}", 
                loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Credenciales inválidas", "INVALID_CREDENTIALS"));
        } catch (Exception e) {
            log.error("[AUTH-LOGIN] ✗ Error durante login para: {}", 
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
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(HttpServletRequest request) {
        log.info("[AUTH-ME] ========== GET /me ==========");
        log.info("[AUTH-ME] Request headers:");
        request.getHeaderNames().asIterator().forEachRemaining(header -> 
            log.info("[AUTH-ME]   {}: {}", header, 
                header.equalsIgnoreCase("Authorization") ? 
                    (request.getHeader(header).length() > 30 ? 
                        request.getHeader(header).substring(0, 30) + "..." : 
                        request.getHeader(header)) : 
                    request.getHeader(header))
        );
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("[AUTH-ME] Authentication: {}", authentication != null ? 
            authentication.getClass().getSimpleName() : "NULL");
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("[AUTH-ME] ✗ No autenticado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No autenticado", "NOT_AUTHENTICATED"));
        }

        String username = authentication.getName();
        log.info("[AUTH-ME] ✓ Usuario autenticado: {}", username);
        User user = userService.findByUsername(username);
        
        UserDto dto = UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();

        log.info("[AUTH-ME] ✓ Retornando datos del usuario: {}", username);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Cambiar contraseña",
            description = "Permite al usuario autenticado cambiar su contraseña.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Contraseña actualizada exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Errores de validación"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "No autenticado o contraseña actual incorrecta"
            )
    })
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No autenticado", "NOT_AUTHENTICATED"));
        }

        String username = authentication.getName();
        
        try {
            // Validación: confirmPassword debe coincidir con newPassword
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(
                            "Las contraseñas no coinciden", 
                            "PASSWORDS_NOT_MATCH"));
            }
            
            // Validación: newPassword no debe ser igual a currentPassword
            User user = userService.findByUsername(username);
            if (request.getCurrentPassword().equals(request.getNewPassword())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(
                            "La nueva contraseña debe ser diferente a la actual", 
                            "PASSWORD_SAME"));
            }
            
            // Validación: contraseñas débiles comunes
            String[] weakPasswords = {"changeme", "admin", "password"};
            String newPasswordLower = request.getNewPassword().toLowerCase();
            for (String weak : weakPasswords) {
                if (newPasswordLower.equals(weak)) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(
                                "No puede usar '" + weak + "' como contraseña", 
                                "WEAK_PASSWORD"));
                }
            }
            
            // Cambiar contraseña (este método ya valida currentPassword)
            userService.changePassword(username, request.getCurrentPassword(), request.getNewPassword());
            
            log.info("[API] Contraseña actualizada exitosamente para usuario: {}", username);
            
            return ResponseEntity.ok(ApiResponse.success("Contraseña actualizada exitosamente"));
            
        } catch (RuntimeException e) {
            // Si es error de contraseña incorrecta
            if (e.getMessage().contains("incorrecta")) {
                log.warn("[API] Intento de cambio de contraseña con contraseña actual incorrecta: {}", username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(
                            "La contraseña actual es incorrecta", 
                            "INVALID_PASSWORD"));
            }
            
            log.error("[API] Error al cambiar contraseña para usuario: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(
                        "Error al cambiar contraseña: " + e.getMessage(), 
                        "PASSWORD_CHANGE_ERROR"));
        }
    }
}
