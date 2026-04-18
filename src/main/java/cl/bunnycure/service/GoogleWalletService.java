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

    public String createWalletLink(Customer customer) {
        try {
            ServiceAccountCredentials credentials = getCredentials();
            String serviceAccountEmail = credentials.getClientEmail();
            PrivateKey privateKey = credentials.getPrivateKey();

            String classId = String.format("%s.%s", issuerId, loyaltyClass);
            // Usamos un ID simple sin guiones para evitar problemas de URL
            String objectId = String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_"));

            // 1. Objeto de fidelización (Casing compatible con REST)
            Map<String, Object> loyaltyObject = new LinkedHashMap<>();
            loyaltyObject.put("id", objectId);
            loyaltyObject.put("classId", classId);
            loyaltyObject.put("state", "active"); // Usamos minúsculas como en el ejemplo exitoso
            loyaltyObject.put("accountName", customer.getFullName());
            loyaltyObject.put("accountId", customer.getPhone().replace("+", ""));
            
            // Puntos de fidelidad
            Map<String, Object> loyaltyPoints = new LinkedHashMap<>();
            Map<String, Object> balance = new LinkedHashMap<>();
            balance.put("int", customer.getLoyaltyStamps());
            loyaltyPoints.put("balance", balance);
            loyaltyPoints.put("label", "Sellos");
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            // Código de barras
            Map<String, Object> barcode = new LinkedHashMap<>();
            barcode.put("type", "qrCode"); // camelCase
            barcode.put("value", customer.getPhone());
            barcode.put("alternateText", customer.getPhone());
            loyaltyObject.put("barcode", barcode);

            // 2. Payload del JWT
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            // 3. Ajuste de tiempos (IAT 1 hora atrás por seguridad de reloj)
            long now = (System.currentTimeMillis() / 1000L);
            long iat = now - 3600L; // 1 hora en el pasado
            long exp = now + 3600L; // 1 hora en el futuro

            // 4. Firmar el JWT (Formato Plano)
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

            log.info("[Wallet] New token generated for customer ID {}", customer.getId());
            return "https://pay.google.com/gp/v/save/" + jwt;

        } catch (Exception e) {
            log.error("Error en GoogleWalletService: {}", e.getMessage());
            throw new RuntimeException("Error al generar enlace de Wallet", e);
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
        // La lógica push se mantiene igual
    }
}
