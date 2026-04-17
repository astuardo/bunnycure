package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import com.google.auth.oauth2.ServiceAccountCredentials;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.security.PrivateKey;
import java.util.*;

@Slf4j
@Service
public class GoogleWalletService {

    @Value("${bunnycure.google.wallet.issuer-id}")
    private String issuerId;

    @Value("${bunnycure.google.wallet.loyalty-class}")
    private String loyaltyClass;

    @Value("${bunnycure.google.wallet.credentials-path}")
    private String credentialsPath;

    /**
     * Genera un JWT firmado para que el cliente guarde su tarjeta en Google Wallet.
     * Utiliza la API moderna de Google Auth y JJWT 0.12.x.
     */
    public String createWalletLink(Customer customer) {
        try {
            // 1. Cargar credenciales usando ServiceAccountCredentials (API moderna)
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(new FileInputStream(credentialsPath));
            
            String serviceAccountEmail = credentials.getClientEmail();
            PrivateKey privateKey = credentials.getPrivateKey();

            // 2. Definir el objeto de lealtad (Loyalty Object)
            String objectId = String.format("%s.%s", issuerId, customer.getPublicId());
            
            Map<String, Object> loyaltyObject = new HashMap<>();
            loyaltyObject.put("id", objectId);
            loyaltyObject.put("classId", String.format("%s.%s", issuerId, loyaltyClass));
            loyaltyObject.put("state", "ACTIVE");
            
            // Datos del cliente
            loyaltyObject.put("accountName", customer.getFullName());
            loyaltyObject.put("accountId", customer.getPhone());
            
            // Puntos/Sellos actuales
            Map<String, Object> loyaltyPoints = new HashMap<>();
            Map<String, Object> balance = new HashMap<>();
            balance.put("int", customer.getLoyaltyStamps());
            loyaltyPoints.put("balance", balance);
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            // 3. Estructura del Payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            // 4. Generar el JWT usando JJWT 0.12.x (Fluent API moderna)
            long now = System.currentTimeMillis() / 1000L;
            
            String jwt = Jwts.builder()
                    .header().add("typ", "JWT").and()
                    .claim("iss", serviceAccountEmail)
                    .claim("aud", "google")
                    .claim("typ", "google.wallet_object_jwt")
                    .claim("iat", now)
                    .claim("payload", payload)
                    .signWith(privateKey, Jwts.SIG.RS256) // Usar Jwts.SIG para algoritmos modernos
                    .compact();

            return "https://pay.google.com/gp/v/save/" + jwt;

        } catch (Exception e) {
            log.error("Error generating Google Wallet link for customer {}: {}", customer.getId(), e.getMessage(), e);
            throw new RuntimeException("No se pudo generar el enlace de Google Wallet", e);
        }
    }
}
