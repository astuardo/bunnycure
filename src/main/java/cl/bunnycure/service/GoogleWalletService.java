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

    @Value("${GOOGLE_WALLET_CREDENTIALS:}")
    private String credentialsJson;

    /**
     * Genera un JWT firmado con los campos estrictos que Google requiere para Loyalty Cards.
     */
    public String createWalletLink(Customer customer) {
        try {
            ServiceAccountCredentials credentials = getCredentials();
            String serviceAccountEmail = credentials.getClientEmail();
            PrivateKey privateKey = credentials.getPrivateKey();

            String classId = String.format("%s.%s", issuerId, loyaltyClass);
            String objectId = String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_"));

            // 1. Loyalty Object - Formato estrictamente compatible con la API REST
            Map<String, Object> loyaltyObject = new LinkedHashMap<>();
            loyaltyObject.put("id", objectId);
            loyaltyObject.put("classId", classId);
            loyaltyObject.put("state", "active");
            
            // Campos obligatorios para visualización
            loyaltyObject.put("accountName", customer.getFullName());
            loyaltyObject.put("accountId", customer.getPhone().replace("+", ""));
            
            // Puntos de fidelidad con estructura completa
            Map<String, Object> loyaltyPoints = new LinkedHashMap<>();
            Map<String, Object> balance = new LinkedHashMap<>();
            balance.put("int", customer.getLoyaltyStamps());
            loyaltyPoints.put("balance", balance);
            loyaltyPoints.put("label", "Sellos");
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            // Código de barras (A veces requerido para que el botón funcione)
            Map<String, Object> barcode = new LinkedHashMap<>();
            barcode.put("type", "qrCode");
            barcode.put("value", customer.getPhone());
            barcode.put("alternateText", customer.getPhone());
            loyaltyObject.put("barcode", barcode);

            // 2. Payload del JWT
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            // 3. Claims del JWT - Tiempos y Audiencia
            long now = System.currentTimeMillis() / 1000L;
            long iat = now - 60L; // 1 minuto atrás por seguridad
            long exp = now + 7200L; // Válido por 2 horas

            String jwt = Jwts.builder()
                    .header().add("typ", "JWT").add("alg", "RS256").and()
                    .claim("iss", serviceAccountEmail)
                    .claim("aud", "google")
                    .claim("typ", "savetowallet")
                    .claim("iat", iat)
                    .claim("exp", exp)
                    .claim("payload", payload)
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            log.info("[Wallet] JWT generated for client: {}. Object ID: {}", customer.getFullName(), objectId);
            return "https://pay.google.com/gp/v/save/" + jwt;

        } catch (Exception e) {
            log.error("Error en GoogleWalletService al generar link: {}", e.getMessage());
            throw new RuntimeException("Error en integración con Wallet", e);
        }
    }

    private ServiceAccountCredentials getCredentials() throws Exception {
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return ServiceAccountCredentials.fromStream(new java.io.ByteArrayInputStream(credentialsJson.getBytes()));
        } else {
            return ServiceAccountCredentials.fromStream(new FileInputStream(credentialsPath));
        }
    }

    public void updateCustomerStamps(Customer customer) {
        // ... misma lógica de sincronización push
    }
}
