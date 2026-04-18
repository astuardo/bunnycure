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
            // Simplificamos el ID para evitar cualquier problema con caracteres
            String objectId = String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_"));

            // 1. Objeto de fidelización (Estructura mínima recomendada)
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

            // 2. Payload del JWT
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            // 3. Orígenes permitidos (Añadimos todas las variantes posibles)
            List<String> origins = Arrays.asList(
                "http://localhost:5173",
                "https://bunnycure-frontend.vercel.app",
                "https://bunnycure.cl",
                "https://www.bunnycure.cl"
            );

            long now = (System.currentTimeMillis() / 1000L) - 30L; // 30s atrás por seguridad
            long exp = now + 3600L;

            // 4. Construcción del JWT (Formato Plano compatible con Google)
            String jwt = Jwts.builder()
                    .setHeaderParam("typ", "JWT")
                    .setHeaderParam("alg", "RS256")
                    .claim("iss", serviceAccountEmail)
                    .claim("aud", "google") // Audiencia como String simple
                    .claim("typ", "savetowallet")
                    .claim("iat", now)
                    .claim("exp", exp)
                    .claim("origins", origins)
                    .claim("payload", payload)
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            log.info("[Wallet] Success generating link for {}", customer.getFullName());
            return "https://pay.google.com/gp/v/save/" + jwt;

        } catch (Exception e) {
            log.error("Error en GoogleWalletService: {}", e.getMessage());
            throw new RuntimeException("No se pudo generar el enlace de Wallet", e);
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
        // La lógica de actualización push se mantiene igual
    }
}
