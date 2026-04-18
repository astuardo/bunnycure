package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.walletobjects.Walletobjects;
import com.google.api.services.walletobjects.model.Barcode;
import com.google.api.services.walletobjects.model.LoyaltyObject;
import com.google.api.services.walletobjects.model.LoyaltyPoints;
import com.google.api.services.walletobjects.model.LoyaltyPointsBalance;
import com.google.api.services.walletobjects.model.TextModuleData;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.*;
import java.util.Base64;

@Slf4j
@Service
public class GoogleWalletService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

            String classId = buildClassId();
            String objectId = buildObjectId(customer);
            String phone = normalizePhone(customer.getPhone());

            // 1. Construir el Loyalty Object (Formato exacto del dashboard)
            Map<String, Object> loyaltyObject = new LinkedHashMap<>();
            loyaltyObject.put("id", objectId);
            loyaltyObject.put("classId", classId);
            loyaltyObject.put("state", "ACTIVE");
            loyaltyObject.put("accountName", customer.getFullName() != null && !customer.getFullName().isBlank() ? customer.getFullName() : "Cliente BunnyCure");
            loyaltyObject.put("accountId", phone);
            
            Map<String, Object> loyaltyPoints = new LinkedHashMap<>();
            Map<String, Object> balance = new LinkedHashMap<>();
            balance.put("int", customer.getLoyaltyStamps());
            loyaltyPoints.put("balance", balance);
            loyaltyPoints.put("label", "Sellos");
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            Map<String, Object> barcode = new LinkedHashMap<>();
            barcode.put("type", "QR_CODE");
            barcode.put("value", phone);
            barcode.put("alternateText", phone);
            loyaltyObject.put("barcode", barcode);

            // 2. Preparar el Payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));

            // 3. Crear Claims manualmente (idéntico al dashboard validado)
            long now = System.currentTimeMillis() / 1000L;
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", serviceAccountEmail);
            claims.put("aud", "google"); 
            claims.put("typ", "savetowallet");
            claims.put("iat", now - 30L); // 30s atrás para evitar fallos de reloj
            
            // Origins es OBLIGATORIO para integraciones web, de lo contrario Google bloquea por seguridad (CSRF)
            List<String> origins = Arrays.asList(
                "http://localhost:5173",
                "https://bunnycure-frontend.vercel.app",
                "https://bunnycure-frontend-astuardo.vercel.app",
                "https://bunnycure.cl",
                "https://www.bunnycure.cl"
            );
            claims.put("origins", origins);
            
            claims.put("payload", payload);

            // 4. Firmar JWT manualmente para mantener aud como String ("google")
            String signedJwt = signWalletJwt(claims, privateKey);

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
        String objectId = buildObjectId(customer);
        int stamps = normalizeStamps(customer.getLoyaltyStamps());

        try {
            Walletobjects walletobjects = getWalletobjectsClient();
            LoyaltyObject loyaltyObject = getOrCreateLoyaltyObject(walletobjects, customer);

            loyaltyObject.setLoyaltyPoints(buildLoyaltyPoints(stamps));
            loyaltyObject.setTextModulesData(mergeProgressModule(loyaltyObject.getTextModulesData(), stamps));

            walletobjects.loyaltyobject().update(objectId, loyaltyObject).execute();
            log.info("[Wallet] Loyalty object updated: objectId={}, stamps={}", objectId, stamps);
        } catch (Exception e) {
            log.error("[Wallet] Error updating loyalty object for objectId={}, stamps={}: {}",
                    objectId, stamps, e.getMessage(), e);
        }
    }

    private String signWalletJwt(Map<String, Object> claims, PrivateKey privateKey) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "RS256");

        String encodedHeader = toBase64Url(OBJECT_MAPPER.writeValueAsBytes(header));
        String encodedPayload = toBase64Url(OBJECT_MAPPER.writeValueAsBytes(claims));
        String signingInput = encodedHeader + "." + encodedPayload;

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = toBase64Url(signer.sign());

        return signingInput + "." + encodedSignature;
    }

    private String toBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Walletobjects getWalletobjectsClient() throws Exception {
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(getCredentials());
        return new Walletobjects.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer
        ).setApplicationName("BunnyCure").build();
    }

    private LoyaltyObject getOrCreateLoyaltyObject(Walletobjects walletobjects, Customer customer) throws Exception {
        String objectId = buildObjectId(customer);
        try {
            return walletobjects.loyaltyobject().get(objectId).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }

            int stamps = normalizeStamps(customer.getLoyaltyStamps());
            LoyaltyObject newObject = new LoyaltyObject()
                    .setId(objectId)
                    .setClassId(buildClassId())
                    .setState("ACTIVE")
                    .setAccountName(resolveAccountName(customer))
                    .setAccountId(normalizePhone(customer.getPhone()))
                    .setBarcode(new Barcode()
                            .setType("QR_CODE")
                            .setValue(normalizePhone(customer.getPhone()))
                            .setAlternateText(normalizePhone(customer.getPhone())))
                    .setLoyaltyPoints(buildLoyaltyPoints(stamps))
                    .setTextModulesData(Collections.singletonList(buildProgressModule(stamps)));

            LoyaltyObject created = walletobjects.loyaltyobject().insert(newObject).execute();
            log.info("[Wallet] Loyalty object created for sync: objectId={}, stamps={}", objectId, stamps);
            return created;
        }
    }

    private LoyaltyPoints buildLoyaltyPoints(int stamps) {
        return new LoyaltyPoints()
                .setLabel("Sellos")
                .setBalance(new LoyaltyPointsBalance().setInt(stamps));
    }

    private List<TextModuleData> mergeProgressModule(List<TextModuleData> existingModules, int stamps) {
        List<TextModuleData> modules = existingModules == null ? new ArrayList<>() : new ArrayList<>(existingModules);
        modules.removeIf(module -> "stamp_progress".equals(module.getId()));
        modules.add(buildProgressModule(stamps));
        return modules;
    }

    private TextModuleData buildProgressModule(int stamps) {
        int boundedStamps = Math.max(0, Math.min(10, stamps));
        StringBuilder visual = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            visual.append(i < boundedStamps ? '●' : '○');
        }

        return new TextModuleData()
                .setId("stamp_progress")
                .setHeader("Progreso de estampillas")
                .setBody(visual + " (" + boundedStamps + "/10)");
    }

    private String buildClassId() {
        return String.format("%s.%s", issuerId, loyaltyClass);
    }

    private String buildObjectId(Customer customer) {
        return String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_"));
    }

    private int normalizeStamps(Integer loyaltyStamps) {
        return Math.max(0, loyaltyStamps == null ? 0 : loyaltyStamps);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "000000000";
        }
        return phone.replace("+", "");
    }

    private String resolveAccountName(Customer customer) {
        return customer.getFullName() != null && !customer.getFullName().isBlank()
                ? customer.getFullName()
                : "Cliente BunnyCure";
    }
}
