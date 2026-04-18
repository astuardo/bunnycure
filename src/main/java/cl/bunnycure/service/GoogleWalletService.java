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
     * Genera un JWT firmado siguiendo el estándar estricto de Google Wallet.
     */
    public String createWalletLink(Customer customer) {
        try {
            ServiceAccountCredentials credentials = getCredentials();
            String serviceAccountEmail = credentials.getClientEmail();
            PrivateKey privateKey = credentials.getPrivateKey();

            // Formato de IDs: issuerId.uniqueId
            String classId = String.format("%s.%s", issuerId, loyaltyClass);
            String objectId = String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_"));

            // 1. Definir el Loyalty Object (Cuerpo del pase)
            Map<String, Object> loyaltyObject = new HashMap<>();
            loyaltyObject.put("id", objectId);
            loyaltyObject.put("classId", classId);
            loyaltyObject.put("state", "ACTIVE");
            loyaltyObject.put("accountName", customer.getFullName());
            loyaltyObject.put("accountId", customer.getPhone().replace("+", ""));
            
            // Puntos de fidelidad
            Map<String, Object> loyaltyPoints = new HashMap<>();
            Map<String, Object> balance = new HashMap<>();
            balance.put("int", customer.getLoyaltyStamps());
            loyaltyPoints.put("balance", balance);
            loyaltyPoints.put("label", "Sellos");
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            // Código de barras (QR con el teléfono o ID)
            Map<String, Object> barcode = new HashMap<>();
            barcode.put("type", "QR_CODE");
            barcode.put("value", customer.getPhone());
            barcode.put("alternateText", customer.getPhone());
            loyaltyObject.put("barcode", barcode);

            // 2. Estructura del Payload del JWT
            Map<String, Object> payload = new HashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            // 3. Orígenes permitidos (Google Wallet es muy estricto con esto)
            List<String> origins = Arrays.asList(
                "http://localhost:5173",
                "https://bunnycure-frontend.vercel.app",
                "https://bunnycure.cl"
            );

            long now = (System.currentTimeMillis() / 1000L) - 10L; // 10s atrás por seguridad
            long exp = now + 3600L;

            // 4. Firmar el JWT con JJWT 0.12.x
            String jwt = Jwts.builder()
                    .header().add("typ", "JWT").and()
                    .claim("iss", serviceAccountEmail)
                    .claim("aud", "google")
                    .claim("typ", "savetowallet")
                    .claim("iat", now)
                    .claim("exp", exp)
                    .claim("origins", origins)
                    .claim("payload", payload)
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            log.info("[Wallet] Generated link for customer: {}", customer.getFullName());
            return "https://pay.google.com/gp/v/save/" + jwt;

        } catch (Exception e) {
            log.error("Error generating Google Wallet link: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar enlace de Google Wallet", e);
        }
    }

    private ServiceAccountCredentials getCredentials() throws Exception {
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return ServiceAccountCredentials.fromStream(
                new java.io.ByteArrayInputStream(credentialsJson.getBytes())
            );
        } else {
            return ServiceAccountCredentials.fromStream(new FileInputStream(credentialsPath));
        }
    }
    
    // ... método updateCustomerStamps permanece igual
    public void updateCustomerStamps(Customer customer) {
        // Implementación anterior (PATCH API)
    }
}
