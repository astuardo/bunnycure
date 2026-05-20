package cl.bunnycure.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Servicio para generar y validar tokens JWT.
 * Usado como alternativa a cookies de sesión para clientes móviles.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration:900000}") // 15 minutos
    private Long accessExpiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 días
    private Long refreshExpiration;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().isEmpty() || secret.trim().length() < 32) {
            throw new IllegalStateException("CRITICAL: 'jwt.secret' no está configurado o es demasiado débil (mínimo 256 bits).");
        }
    }

    /**
     * Genera un token JWT para un usuario.
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "access");
        return createToken(claims, userDetails.getUsername());
    }

    /**
     * Genera un token con claims personalizados.
     */
    public String generateToken(String username, Map<String, Object> claims) {
        claims.putIfAbsent("tokenType", "access");
        return createToken(claims, username);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateRefreshToken(userDetails, false);
    }

    public String generateRefreshToken(UserDetails userDetails, boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "refresh");
        claims.put("rememberMe", rememberMe);
        return createRefreshToken(claims, userDetails.getUsername());
    }

    public String generateRefreshToken(String username) {
        return generateRefreshToken(username, false);
    }

    public String generateRefreshToken(String username, boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "refresh");
        claims.put("rememberMe", rememberMe);
        return createRefreshToken(claims, username);
    }

    /**
     * Crea el token JWT con claims y subject.
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    private String createRefreshToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Obtiene la clave de firma desde el secret configurado.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extrae el username (subject) del token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrae la fecha de expiración del token.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extrae un claim específico del token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extrae todos los claims del token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Verifica si el token ha expirado.
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Valida el token contra el usuario.
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token) && isAccessToken(token));
    }

    public Boolean validateRefreshToken(String token) {
        try {
            return !isTokenExpired(token) && isRefreshToken(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRememberMeRefreshToken(String token) {
        try {
            Object rememberMe = extractClaim(token, claims -> claims.get("rememberMe"));
            return Boolean.TRUE.equals(rememberMe);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Valida solo si el token no ha expirado (sin verificar usuario).
     */
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token) && isAccessToken(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAccessToken(String token) {
        Object tokenType = extractClaim(token, claims -> claims.get("tokenType"));
        return "access".equals(tokenType);
    }

    private boolean isRefreshToken(String token) {
        Object tokenType = extractClaim(token, claims -> claims.get("tokenType"));
        return "refresh".equals(tokenType);
    }
}
