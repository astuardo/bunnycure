package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.walletobjects.Walletobjects;
import com.google.api.services.walletobjects.model.Barcode;
import com.google.api.services.walletobjects.model.GenericObject;
import com.google.api.services.walletobjects.model.Image;
import com.google.api.services.walletobjects.model.ImageUri;
import com.google.api.services.walletobjects.model.LocalizedString;
import com.google.api.services.walletobjects.model.TranslatedString;
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

    @Value("${bunnycure.google.wallet.generic-class:bunnycure_generic_card}")
    private String genericClass;

    @Value("${bunnycure.google.wallet.pass-type:loyalty}")
    private String passType;

    @Value("${bunnycure.google.wallet.credentials-path}")
    private String credentialsPath;

    @Value("${GOOGLE_WALLET_CREDENTIALS:}")
    private String credentialsJson;

    @Value("${bunnycure.google.wallet.hero-base-url}")
    private String heroBaseUrl;

    @Value("${bunnycure.google.wallet.hero-image-extension:svg}")
    private String heroImageExtension;

    /**
     * Genera un JWT firmado para Google Wallet.
     * Esta versión asegura que 'aud' sea un String y no un Array.
     */
    public String createWalletLink(Customer customer) {
        try {
            ServiceAccountCredentials credentials = getCredentials();
            PrivateKey privateKey = credentials.getPrivateKey();
            String serviceAccountEmail = credentials.getClientEmail();

            String classId = issueGenericPass() ? buildGenericClassId() : buildLoyaltyClassId();
            String objectId = issueGenericPass() ? buildGenericObjectId(customer) : buildLoyaltyObjectId(customer);
            String phone = normalizePhone(customer.getPhone());
            int stamps = normalizeStamps(customer.getLoyaltyStamps());

            // 2. Preparar el Payload
            Map<String, Object> payload = new LinkedHashMap<>();
            if (issueGenericPass()) {
                payload.put("genericObjects", Collections.singletonList(
                        buildGenericObjectPayload(objectId, classId, phone, stamps, customer))
                );
            } else {
                payload.put("loyaltyObjects", Collections.singletonList(
                        buildLoyaltyObjectPayload(objectId, classId, customer, phone, stamps))
                );
            }

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

            log.info("[Wallet] Link generated for customer: {}, passType={}, objectId={}",
                    customer.getFullName(), normalizedPassType(), objectId);
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
        int stamps = normalizeStamps(customer.getLoyaltyStamps());

        try {
            Walletobjects walletobjects = getWalletobjectsClient();
            if (syncLoyaltyPass()) {
                syncLoyaltyObject(walletobjects, customer, stamps);
            }
            if (syncGenericPass()) {
                syncGenericObject(walletobjects, customer, stamps);
            }
        } catch (Exception e) {
            log.error("[Wallet] Error updating wallet object(s) for customerPublicId={}, stamps={}: {}",
                    customer.getPublicId(), stamps, e.getMessage(), e);
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
        String objectId = buildLoyaltyObjectId(customer);
        try {
            return walletobjects.loyaltyobject().get(objectId).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }

            int stamps = normalizeStamps(customer.getLoyaltyStamps());
            LoyaltyObject newObject = new LoyaltyObject()
                    .setId(objectId)
                    .setClassId(buildLoyaltyClassId())
                    .setState("ACTIVE")
                    .setAccountName(resolveAccountName(customer))
                    .setAccountId(normalizePhone(customer.getPhone()))
                    .setBarcode(new Barcode()
                            .setType("QR_CODE")
                            .setValue(normalizePhone(customer.getPhone()))
                            .setAlternateText(normalizePhone(customer.getPhone())))
                    .setLoyaltyPoints(buildLoyaltyPoints(stamps))
                    .setTextModulesData(Collections.singletonList(buildProgressModule(stamps)))
                    .setHeroImage(buildHeroImage(stamps));

            LoyaltyObject created = walletobjects.loyaltyobject().insert(newObject).execute();
            log.info("[Wallet] Loyalty object created for sync: objectId={}, stamps={}", objectId, stamps);
            return created;
        }
    }

    private GenericObject getOrCreateGenericObject(Walletobjects walletobjects, Customer customer) throws Exception {
        String objectId = buildGenericObjectId(customer);
        try {
            return walletobjects.genericobject().get(objectId).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }

            int stamps = normalizeStamps(customer.getLoyaltyStamps());
            String phone = normalizePhone(customer.getPhone());
            GenericObject newObject = new GenericObject()
                    .setId(objectId)
                    .setClassId(buildGenericClassId())
                    .setCardTitle(new LocalizedString()
                            .setDefaultValue(new TranslatedString()
                                    .setLanguage("es-CL")
                                    .setValue("BunnyCure Loyalty")))
                    .setHeader(new LocalizedString()
                            .setDefaultValue(new TranslatedString()
                                    .setLanguage("es-CL")
                                    .setValue(resolveAccountName(customer))))
                    .setState("ACTIVE")
                    .setBarcode(buildBarcode(phone))
                    .setHeroImage(buildHeroImage(stamps))
                    .setTextModulesData(new ArrayList<>(buildGenericModules(customer, stamps)));

            GenericObject created = walletobjects.genericobject().insert(newObject).execute();
            log.info("[Wallet] Generic object created for sync: objectId={}, stamps={}", objectId, stamps);
            return created;
        }
    }

    private void syncLoyaltyObject(Walletobjects walletobjects, Customer customer, int stamps) throws Exception {
        String objectId = buildLoyaltyObjectId(customer);
        LoyaltyObject loyaltyObject = getOrCreateLoyaltyObject(walletobjects, customer);
        loyaltyObject.setLoyaltyPoints(buildLoyaltyPoints(stamps));
        loyaltyObject.setTextModulesData(mergeProgressModule(loyaltyObject.getTextModulesData(), stamps));
        loyaltyObject.setHeroImage(buildHeroImage(stamps));
        walletobjects.loyaltyobject().update(objectId, loyaltyObject).execute();
        log.info("[Wallet] Loyalty object updated: objectId={}, stamps={}", objectId, stamps);
    }

    private void syncGenericObject(Walletobjects walletobjects, Customer customer, int stamps) throws Exception {
        String objectId = buildGenericObjectId(customer);
        GenericObject genericObject = getOrCreateGenericObject(walletobjects, customer);
        genericObject.setCardTitle(new LocalizedString()
                .setDefaultValue(new TranslatedString()
                        .setLanguage("es-CL")
                        .setValue("BunnyCure Loyalty")));
        genericObject.setHeader(new LocalizedString()
                .setDefaultValue(new TranslatedString()
                        .setLanguage("es-CL")
                        .setValue(resolveAccountName(customer))));
        genericObject.setHeroImage(buildHeroImage(stamps));
        genericObject.setBarcode(buildBarcode(normalizePhone(customer.getPhone())));
        genericObject.setTextModulesData(new ArrayList<>(mergeGenericModules(genericObject.getTextModulesData(), customer, stamps)));
        walletobjects.genericobject().update(objectId, genericObject).execute();
        log.info("[Wallet] Generic object updated: objectId={}, stamps={}", objectId, stamps);
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

    private List<TextModuleData> buildGenericModules(Customer customer, int stamps) {
        List<TextModuleData> modules = new ArrayList<>();
        modules.add(new TextModuleData()
                .setId("customer_name")
                .setHeader("Nombre")
                .setBody(resolveAccountName(customer)));
        modules.add(new TextModuleData()
                .setId("customer_phone")
                .setHeader("Telefono")
                .setBody(normalizePhone(customer.getPhone())));
        modules.add(buildProgressModule(stamps));
        return modules;
    }

    private List<TextModuleData> mergeGenericModules(List<TextModuleData> existingModules, Customer customer, int stamps) {
        List<TextModuleData> modules = existingModules == null ? new ArrayList<>() : new ArrayList<>(existingModules);
        modules.removeIf(module -> module != null && (
                "stamp_progress".equals(module.getId())
                        || "customer_name".equals(module.getId())
                        || "customer_phone".equals(module.getId()))
        );
        modules.addAll(buildGenericModules(customer, stamps));
        return modules;
    }

    private Image buildHeroImage(int stamps) {
        return new Image()
                .setSourceUri(new ImageUri().setUri(buildHeroImageUrl(stamps)));
    }

    private Map<String, Object> buildHeroImagePayload(int stamps) {
        Map<String, Object> sourceUri = new LinkedHashMap<>();
        sourceUri.put("uri", buildHeroImageUrl(stamps));
        Map<String, Object> heroImage = new LinkedHashMap<>();
        heroImage.put("sourceUri", sourceUri);
        return heroImage;
    }

    private Barcode buildBarcode(String phone) {
        return new Barcode()
                .setType("QR_CODE")
                .setValue(phone)
                .setAlternateText(phone);
    }

    private Map<String, Object> buildLoyaltyObjectPayload(String objectId, String classId, Customer customer, String phone, int stamps) {
        Map<String, Object> loyaltyObject = new LinkedHashMap<>();
        loyaltyObject.put("id", objectId);
        loyaltyObject.put("classId", classId);
        loyaltyObject.put("state", "ACTIVE");
        loyaltyObject.put("accountName", resolveAccountName(customer));
        loyaltyObject.put("accountId", phone);

        Map<String, Object> loyaltyPoints = new LinkedHashMap<>();
        Map<String, Object> balance = new LinkedHashMap<>();
        balance.put("int", stamps);
        loyaltyPoints.put("balance", balance);
        loyaltyPoints.put("label", "Sellos");
        loyaltyObject.put("loyaltyPoints", loyaltyPoints);

        Map<String, Object> barcode = new LinkedHashMap<>();
        barcode.put("type", "QR_CODE");
        barcode.put("value", phone);
        barcode.put("alternateText", phone);
        loyaltyObject.put("barcode", barcode);
        loyaltyObject.put("heroImage", buildHeroImagePayload(stamps));
        loyaltyObject.put("textModulesData", Collections.singletonList(buildProgressModulePayload(stamps)));
        return loyaltyObject;
    }

    private Map<String, Object> buildGenericObjectPayload(String objectId, String classId, String phone, int stamps, Customer customer) {
        Map<String, Object> genericObject = new LinkedHashMap<>();
        genericObject.put("id", objectId);
        genericObject.put("classId", classId);
        genericObject.put("state", "ACTIVE");

        Map<String, Object> cardTitle = new LinkedHashMap<>();
        Map<String, Object> cardTitleValue = new LinkedHashMap<>();
        cardTitleValue.put("language", "es-CL");
        cardTitleValue.put("value", "BunnyCure Loyalty");
        cardTitle.put("defaultValue", cardTitleValue);
        genericObject.put("cardTitle", cardTitle);

        Map<String, Object> header = new LinkedHashMap<>();
        Map<String, Object> headerValue = new LinkedHashMap<>();
        headerValue.put("language", "es-CL");
        headerValue.put("value", resolveAccountName(customer));
        header.put("defaultValue", headerValue);
        genericObject.put("header", header);

        Map<String, Object> barcode = new LinkedHashMap<>();
        barcode.put("type", "QR_CODE");
        barcode.put("value", phone);
        barcode.put("alternateText", phone);
        genericObject.put("barcode", barcode);
        genericObject.put("heroImage", buildHeroImagePayload(stamps));
        genericObject.put("textModulesData", Collections.singletonList(buildProgressModulePayload(stamps)));
        return genericObject;
    }

    private Map<String, Object> buildProgressModulePayload(int stamps) {
        int boundedStamps = Math.max(0, Math.min(10, stamps));
        StringBuilder visual = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            visual.append(i < boundedStamps ? '●' : '○');
        }
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("id", "stamp_progress");
        module.put("header", "Progreso de estampillas");
        module.put("body", visual + " (" + boundedStamps + "/10)");
        return module;
    }

    private String buildLoyaltyClassId() {
        return String.format("%s.%s", issuerId, loyaltyClass);
    }

    private String buildGenericClassId() {
        return String.format("%s.%s", issuerId, genericClass);
    }

    private String buildLoyaltyObjectId(Customer customer) {
        return String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_"));
    }

    private String buildGenericObjectId(Customer customer) {
        return String.format("%s.%s_g", issuerId, customer.getPublicId().replace("-", "_"));
    }

    private int normalizeStamps(Integer loyaltyStamps) {
        return Math.max(0, loyaltyStamps == null ? 0 : loyaltyStamps);
    }

    private String buildHeroImageUrl(int stamps) {
        int boundedStamps = Math.max(0, Math.min(10, stamps));
        String base = heroBaseUrl.endsWith("/") ? heroBaseUrl.substring(0, heroBaseUrl.length() - 1) : heroBaseUrl;
        String extension = (heroImageExtension == null || heroImageExtension.isBlank())
                ? "svg"
                : heroImageExtension.trim().replace(".", "");
        return base + "/hero_" + boundedStamps + "." + extension;
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

    private String normalizedPassType() {
        return passType == null ? "loyalty" : passType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean issueGenericPass() {
        String mode = normalizedPassType();
        return "generic".equals(mode) || "dual".equals(mode);
    }

    private boolean syncLoyaltyPass() {
        String mode = normalizedPassType();
        return "loyalty".equals(mode) || "dual".equals(mode);
    }

    private boolean syncGenericPass() {
        String mode = normalizedPassType();
        return "generic".equals(mode) || "dual".equals(mode);
    }
}
