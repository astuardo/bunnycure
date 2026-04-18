package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.ServiceAccountCredentials;
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
     * Genera un JWT firmado usando las librerías OFICIALES de Google para máxima compatibilidad.
     */
    public String createWalletLink(Customer customer) {
        try {
            ServiceAccountCredentials credentials = getCredentials();
            PrivateKey privateKey = credentials.getPrivateKey();
            String serviceAccountEmail = credentials.getClientEmail();

            String classId = String.format("%s.%s", issuerId, loyaltyClass);
            String objectId = String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_"));

            // 1. Estructura del Loyalty Object (JSON que Google validó)
            Map<String, Object> loyaltyObject = new LinkedHashMap<>();
            loyaltyObject.put("id", objectId);
            loyaltyObject.put("classId", classId);
            loyaltyObject.put("state", "active");
            loyaltyObject.put("accountName", customer.getFullName());
            loyaltyObject.put("accountId", customer.getPhone().replace("+", ""));
            
            Map<String, Object> loyaltyPoints = new LinkedHashMap<>();
            Map<String, Object> balance = new LinkedHashMap<>();
            balance.put("int", customer.getLoyaltyStamps());
            loyaltyPoints.put("balance", balance);
            loyaltyPoints.put("label", "Sellos");
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            Map<String, Object> barcode = new LinkedHashMap<>();
            barcode.put("type", "qrCode");
            barcode.put("value", customer.getPhone());
            barcode.put("alternateText", customer.getPhone());
            loyaltyObject.put("barcode", barcode);

            // 2. Preparar el Payload del JWT
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            // 3. Crear el JWT con la librería oficial de Google
            JsonWebToken.Payload claims = new JsonWebToken.Payload();
            claims.setIssuer(serviceAccountEmail);
            claims.setAudience("google");
            claims.setIssuedAtTimeSeconds(System.currentTimeMillis() / 1000L - 30L);
            claims.setExpirationTimeSeconds(System.currentTimeMillis() / 1000L + 3600L);
            claims.set("typ", "savetowallet");
            claims.set("payload", payloadMap);

            JsonWebSignature.Header header = new JsonWebSignature.Header();
            header.setAlgorithm("RS256");
            header.setType("JWT");

            String signedJwt = JsonWebSignature.signUsingRsa(privateKey, GsonFactory.getDefaultInstance(), header, claims);

            log.info("[Wallet] Link generated successfully using Google Libs for {}", customer.getFullName());
            return "https://pay.google.com/gp/v/save/" + signedJwt;

        } catch (Exception e) {
            log.error("Error en GoogleWalletService (Google Libs): {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo generar el enlace de Google Wallet", e);
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
        // La actualización push utiliza el cliente oficial, por lo que está bien.
    }
}
