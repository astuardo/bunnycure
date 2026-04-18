package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.walletobjects.Walletobjects;
import com.google.api.services.walletobjects.model.LoyaltyPoints;
import com.google.api.services.walletobjects.model.LoyaltyPointsBalance;
import com.google.api.services.walletobjects.model.LoyaltyObject;
import com.google.auth.http.HttpCredentialsAdapter;
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

    private Walletobjects client;

    /**
     * Inicializa el cliente de API de Google Wallet.
     */
    private Walletobjects getClient() throws Exception {
        if (this.client != null) return this.client;

        ServiceAccountCredentials credentials = getCredentials();
        this.client = new Walletobjects.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("BunnyCure")
                .build();
        return this.client;
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

    /**
     * Genera un JWT firmado para que el cliente guarde su tarjeta en Google Wallet.
     */
    public String createWalletLink(Customer customer) {
        try {
            ServiceAccountCredentials credentials = getCredentials();
            String serviceAccountEmail = credentials.getClientEmail();
            PrivateKey privateKey = credentials.getPrivateKey();

            String objectId = String.format("%s.%s", issuerId, customer.getPublicId());
            String classId = String.format("%s.%s", issuerId, loyaltyClass);
            
            log.info("[Wallet Debug] Issuer ID: {}", issuerId);
            log.info("[Wallet Debug] Service Account: {}", serviceAccountEmail);
            log.info("[Wallet Debug] Class ID: {}", classId);
            log.info("[Wallet Debug] Object ID: {}", objectId);

            Map<String, Object> loyaltyObject = new HashMap<>();
            loyaltyObject.put("id", objectId);
            loyaltyObject.put("classId", classId);
            loyaltyObject.put("state", "ACTIVE");
            loyaltyObject.put("accountName", customer.getFullName());
            loyaltyObject.put("accountId", customer.getPhone());
            
            Map<String, Object> loyaltyPoints = new HashMap<>();
            Map<String, Object> balance = new HashMap<>();
            balance.put("int", customer.getLoyaltyStamps());
            loyaltyPoints.put("balance", balance);
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            Map<String, Object> payload = new HashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            long now = System.currentTimeMillis() / 1000L;
            List<String> origins = Arrays.asList("http://localhost:5173", "https://bunnycure-frontend.vercel.app");

            log.info("[Wallet Debug] Origins: {}", origins);

            String jwt = Jwts.builder()
                    .header().add("typ", "JWT").and()
                    .claim("iss", serviceAccountEmail)
                    .claim("aud", "google")
                    .claim("typ", "savetowallet")
                    .claim("iat", now)
                    .claim("origins", origins)
                    .claim("payload", payload)
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            String finalUrl = "https://pay.google.com/gp/v/save/" + jwt;
            log.info("[Wallet Debug] JWT Header/Payload (can be decoded at jwt.io): {}", jwt.substring(0, jwt.lastIndexOf(".")));
            
            return finalUrl;

        } catch (Exception e) {
            log.error("Error generating Google Wallet link for customer {}: {}", customer.getId(), e.getMessage(), e);
            throw new RuntimeException("No se pudo generar el enlace de Google Wallet", e);
        }
    }

    /**
     * Actualiza los sellos en Google Wallet mediante la API REST.
     * Esto hace que el teléfono del cliente reciba una notificación y actualice la tarjeta.
     */
    public void updateCustomerStamps(Customer customer) {
        try {
            String objectId = String.format("%s.%s", issuerId, customer.getPublicId());
            log.info("Pushing update to Google Wallet for customer {}: {} stamps", customer.getFullName(), customer.getLoyaltyStamps());

            // Crear el objeto de actualización parcial (patch)
            LoyaltyObject patchBody = new LoyaltyObject()
                    .setLoyaltyPoints(new LoyaltyPoints()
                            .setBalance(new LoyaltyPointsBalance()
                                    .setInt(customer.getLoyaltyStamps())));

            // Enviar el PATCH a Google
            getClient().loyaltyobject().patch(objectId, patchBody).execute();
            log.info("Google Wallet updated successfully for customer {}", customer.getId());

        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                log.warn("Customer {} has not saved the card to Wallet yet. Skipping push update.", customer.getId());
            } else {
                log.error("Google API error updating Wallet for customer {}: {}", customer.getId(), e.getDetails());
            }
        } catch (Exception e) {
            log.error("Unexpected error updating Google Wallet for customer {}: {}", customer.getId(), e.getMessage());
        }
    }
}
