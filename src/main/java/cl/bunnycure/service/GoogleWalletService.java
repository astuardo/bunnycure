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
     * Genera un JWT firmado para Google Wallet.
     * Esta versión asegura que 'aud' sea un String y no un Array.
     */
    public String createWalletLink(Customer customer) {
        try {
            ServiceAccountCredentials credentials = getCredentials();
            PrivateKey privateKey = credentials.getPrivateKey();
            String serviceAccountEmail = credentials.getClientEmail();

            String classId = String.format("%s.%s", issuerId, loyaltyClass);
            String objectId = String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_"));

            // 1. Construir el Loyalty Object (Formato exacto del dashboard)
            Map<String, Object> loyaltyObject = new LinkedHashMap<>();
            loyaltyObject.put("id", objectId);
            loyaltyObject.put("classId", classId);
            loyaltyObject.put("state", "ACTIVE");
            loyaltyObject.put("accountName", customer.getFullName());
            loyaltyObject.put("accountId", customer.getPhone().replace("+", ""));
            
            Map<String, Object> loyaltyPoints = new LinkedHashMap<>();
            Map<String, Object> balance = new LinkedHashMap<>();
            balance.put("int", customer.getLoyaltyStamps());
            loyaltyPoints.put("balance", balance);
            loyaltyPoints.put("label", "Sellos");
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            Map<String, Object> barcode = new LinkedHashMap<>();
            barcode.put("type", "QR_CODE");
            barcode.put("value", customer.getPhone());
            barcode.put("alternateText", customer.getPhone());
            loyaltyObject.put("barcode", barcode);

            // 2. Preparar el Payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            // 3. Crear Claims manualmente para evitar que JJWT cree arrays en 'aud'
            long now = System.currentTimeMillis() / 1000L;
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", serviceAccountEmail);
            claims.put("aud", "google"); // Forzado como String
            claims.put("typ", "savetowallet");
            claims.put("iat", now - 60L); // 1 min atrás por seguridad
            claims.put("exp", now + 3600L);
            claims.put("payload", payload);

            // 4. Firmar el JWT
            String signedJwt = Jwts.builder()
                    .header().add("typ", "JWT").add("alg", "RS256").and()
                    .claims(claims)
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            log.info("[Wallet] Link generated for customer: {}", customer.getFullName());
            return "https://pay.google.com/gp/v/save/" + signedJwt;

        } catch (Exception e) {
            log.error("Error generating Google Wallet link: {}", e.getMessage(), e);
            throw new RuntimeException("Error en la integración con Google Wallet", e);
        }
    }

    private ServiceAccountCredentials getCredentials() throws Exception {
        java.util.List<String> scopes = Collections.singletonList("https://www.googleapis.com/auth/wallet_object.issuer");
        
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return (ServiceAccountCredentials) ServiceAccountCredentials.fromStream(new java.io.ByteArrayInputStream(credentialsJson.getBytes()))
                    .createScoped(scopes);
        } else {
            return (ServiceAccountCredentials) ServiceAccountCredentials.fromStream(new FileInputStream(credentialsPath))
                    .createScoped(scopes);
        }
    }
    
    public void updateCustomerStamps(Customer customer) {
        // La actualización push está bien
    }
}
