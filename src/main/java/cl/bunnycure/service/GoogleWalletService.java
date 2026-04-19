package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.walletobjects.Walletobjects;
import com.google.api.services.walletobjects.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.net.URLEncoder;
import java.util.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleWalletService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_REWARD_NAME = "Premio BunnyCure";

    private final LoyaltyRewardService loyaltyRewardService;

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

    @Value("${bunnycure.google.wallet.hero-version:v1}")
    private String heroVersion;

    public String createWalletLink(Customer customer) {
        try {
            ServiceAccountCredentials credentials = getCredentials();
            PrivateKey privateKey = credentials.getPrivateKey();
            String serviceAccountEmail = credentials.getClientEmail();

            String classId = issueGenericPass() ? buildGenericClassId() : buildLoyaltyClassId();
            String objectId = issueGenericPass() ? buildGenericObjectId(customer) : buildLoyaltyObjectId(customer);
            String phone = normalizePhone(customer.getPhone());
            int stamps = normalizeStamps(customer.getLoyaltyStamps());
            String rewardName = resolveCurrentRewardName(customer);

            Map<String, Object> payload = new LinkedHashMap<>();
            if (issueGenericPass()) {
                Walletobjects walletobjects = getWalletobjectsClient();
                boolean classExists = syncGenericClassTemplateIfExists(walletobjects, classId);
                if (classExists) {
                    syncGenericObject(walletobjects, customer, stamps);
                } else {
                    log.info("[Wallet] GenericClass {} no existe aún; se creará vía JWT payload en save link", classId);
                }
                payload.put("genericClasses", Collections.singletonList(buildGenericClassPayload(classId)));
                payload.put("genericObjects", Collections.singletonList(
                        buildGenericObjectPayload(objectId, classId, phone, stamps, customer, rewardName))
                );
            } else {
                payload.put("loyaltyObjects", Collections.singletonList(
                        buildLoyaltyObjectPayload(objectId, classId, customer, phone, stamps, rewardName))
                );
            }

            long now = System.currentTimeMillis() / 1000L;
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", serviceAccountEmail);
            claims.put("aud", "google"); 
            claims.put("typ", "savetowallet");
            claims.put("iat", now - 30L);
            
            List<String> origins = Arrays.asList(
                "http://localhost:5173",
                "https://bunnycure-frontend.vercel.app",
                "https://bunnycure-frontend-astuardo.vercel.app",
                "https://bunnycure.cl",
                "https://www.bunnycure.cl"
            );
            claims.put("origins", origins);
            claims.put("payload", payload);

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
        String rewardName = resolveCurrentRewardName(customer);
        try {
            Walletobjects walletobjects = getWalletobjectsClient();
            if (syncLoyaltyPass()) syncLoyaltyObject(walletobjects, customer, stamps, rewardName);
            if (syncGenericPass()) syncGenericObject(walletobjects, customer, stamps);
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

    private GenericObject getOrCreateGenericObject(Walletobjects walletobjects, Customer customer) throws Exception {
        String objectId = buildGenericObjectId(customer);
        try {
            return walletobjects.genericobject().get(objectId).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404) throw e;
            int stamps = normalizeStamps(customer.getLoyaltyStamps());
            String rewardName = resolveCurrentRewardName(customer);
            GenericObject newObject = new GenericObject()
                    .setId(objectId)
                    .setClassId(buildGenericClassId())
                    .setHexBackgroundColor("#c9897a")
                    .setLogo(new Image()
                            .setSourceUri(new ImageUri().setUri("https://www.bunnycure.cl/logo.png"))
                            .setContentDescription(createLocalizedString("LOGO_IMAGE_DESCRIPTION")))
                    .setCardTitle(createLocalizedString("Bunny Cure"))
                    .setSubheader(createLocalizedString("Cliente:"))
                    .setHeader(createLocalizedString(resolveAccountName(customer)))
                    .setState("ACTIVE")
                    .setBarcode(buildBarcode(normalizePhone(customer.getPhone())))
                    .setHeroImage(new Image()
                            .setSourceUri(new ImageUri().setUri(buildHeroImageUrl(stamps, rewardName)))
                            .setContentDescription(createLocalizedString("HERO_IMAGE_DESCRIPTION")))
                    .setTextModulesData(buildGenericModules(stamps, rewardName));
            return walletobjects.genericobject().insert(newObject).execute();
        }
    }

    private void syncGenericObject(Walletobjects walletobjects, Customer customer, int stamps) throws Exception {
        String objectId = buildGenericObjectId(customer);
        String rewardName = resolveCurrentRewardName(customer);
        GenericObject genericObject = getOrCreateGenericObject(walletobjects, customer);
        genericObject.setHexBackgroundColor("#c9897a");
        genericObject.setLogo(new Image()
                .setSourceUri(new ImageUri().setUri("https://www.bunnycure.cl/logo.png"))
                .setContentDescription(createLocalizedString("LOGO_IMAGE_DESCRIPTION")));
        genericObject.setCardTitle(createLocalizedString("Bunny Cure"));
        genericObject.setSubheader(createLocalizedString("Cliente:"));
        genericObject.setHeader(createLocalizedString(resolveAccountName(customer)));
        genericObject.setHeroImage(new Image()
                .setSourceUri(new ImageUri().setUri(buildHeroImageUrl(stamps, rewardName)))
                .setContentDescription(createLocalizedString("HERO_IMAGE_DESCRIPTION")));
        genericObject.setBarcode(buildBarcode(normalizePhone(customer.getPhone())));
        genericObject.setTextModulesData(buildGenericModules(stamps, rewardName));
        walletobjects.genericobject().update(objectId, genericObject).execute();
    }

    private List<TextModuleData> buildGenericModules(int stamps, String rewardName) {
        List<TextModuleData> modules = new ArrayList<>();
        int boundedStamps = Math.max(0, Math.min(10, stamps));
        modules.add(new TextModuleData().setId("sellos").setHeader("Sellos").setBody(boundedStamps + " / 10"));
        modules.add(new TextModuleData().setId("premio:").setHeader("Premio:").setBody(rewardName));
        return modules;
    }

    private LocalizedString createLocalizedString(String value) {
        return new LocalizedString().setDefaultValue(new TranslatedString().setLanguage("en-US").setValue(value));
    }

    private Map<String, Object> buildGenericClassPayload(String classId) {
        Map<String, Object> genericClass = new LinkedHashMap<>();
        genericClass.put("id", classId);
        genericClass.put("classTemplateInfo", buildGenericClassTemplateInfoMap());
        return genericClass;
    }

    private boolean syncGenericClassTemplateIfExists(Walletobjects walletobjects, String classId) throws Exception {
        try {
            GenericClass gc = walletobjects.genericclass().get(classId).execute();
            gc.setClassTemplateInfo(buildGenericClassTemplateInfoModel());
            walletobjects.genericclass().update(classId, gc).execute();
            return true;
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) return false;
            throw e;
        }
    }

    private ClassTemplateInfo buildGenericClassTemplateInfoModel() {
        FieldSelector sellosSelector = new FieldSelector()
                .setFields(Collections.singletonList(
                        new FieldReference().setFieldPath("object.textModulesData['sellos']")
                ));
        FieldSelector premioSelector = new FieldSelector()
                .setFields(Collections.singletonList(
                        new FieldReference().setFieldPath("object.textModulesData['premio:']")
                ));

        CardRowTwoItems twoItems = new CardRowTwoItems()
                .setStartItem(new TemplateItem().setFirstValue(sellosSelector))
                .setEndItem(new TemplateItem().setFirstValue(premioSelector));

        CardRowTemplateInfo row = new CardRowTemplateInfo().setTwoItems(twoItems);
        CardTemplateOverride cardTemplateOverride = new CardTemplateOverride()
                .setCardRowTemplateInfos(Collections.singletonList(row));

        return new ClassTemplateInfo().setCardTemplateOverride(cardTemplateOverride);
    }

    private Map<String, Object> buildGenericClassTemplateInfoMap() {
        Map<String, Object> classTemplateInfo = new LinkedHashMap<>();
        Map<String, Object> cardTemplateOverride = new LinkedHashMap<>();
        List<Map<String, Object>> cardRowTemplateInfos = new ArrayList<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        Map<String, Object> twoItems = new LinkedHashMap<>();

        twoItems.put("startItem", Collections.singletonMap("firstValue", Collections.singletonMap("fields",
                Collections.singletonList(Collections.singletonMap("fieldPath", "object.textModulesData['sellos']")))));
        twoItems.put("endItem", Collections.singletonMap("firstValue", Collections.singletonMap("fields",
                Collections.singletonList(Collections.singletonMap("fieldPath", "object.textModulesData['premio:']")))));

        row1.put("twoItems", twoItems);
        cardRowTemplateInfos.add(row1);
        cardTemplateOverride.put("cardRowTemplateInfos", cardRowTemplateInfos);
        classTemplateInfo.put("cardTemplateOverride", cardTemplateOverride);
        return classTemplateInfo;
    }

    private Map<String, Object> buildGenericObjectPayload(String objectId, String classId, String phone, int stamps, Customer customer, String rewardName) {
        Map<String, Object> genericObject = new LinkedHashMap<>();
        genericObject.put("id", objectId);
        genericObject.put("classId", classId);
        
        // Estilo exacto a object_bunny.json
        genericObject.put("hexBackgroundColor", "#c9897a");
        
        Map<String, Object> logo = new LinkedHashMap<>();
        logo.put("sourceUri", Collections.singletonMap("uri", "https://www.bunnycure.cl/logo.png"));
        logo.put("contentDescription", createLocalizedMap("LOGO_IMAGE_DESCRIPTION", "en-US"));
        genericObject.put("logo", logo);

        genericObject.put("cardTitle", createLocalizedMap("Bunny Cure", "en-US"));
        genericObject.put("subheader", createLocalizedMap("Cliente:", "en-US"));
        genericObject.put("header", createLocalizedMap(resolveAccountName(customer), "en-US"));
        
        genericObject.put("barcode", buildBarcodeMap(phone));
        
        Map<String, Object> heroImage = new LinkedHashMap<>();
        heroImage.put("sourceUri", Collections.singletonMap("uri", buildHeroImageUrl(stamps, rewardName)));
        heroImage.put("contentDescription", createLocalizedMap("HERO_IMAGE_DESCRIPTION", "en-US"));
        genericObject.put("heroImage", heroImage);

        List<Map<String, Object>> modules = new ArrayList<>();
        int boundedStamps = Math.max(0, Math.min(10, stamps));
        modules.add(buildModuleMap("sellos", "Sellos", boundedStamps + " / 10"));
        modules.add(buildModuleMap("premio:", "Premio:", rewardName));
        genericObject.put("textModulesData", modules);
        
        return genericObject;
    }

    private Map<String, Object> createLocalizedMap(String value, String lang) {
        return Collections.singletonMap("defaultValue", Map.of("language", lang, "value", value));
    }

    private Map<String, Object> buildModuleMap(String id, String header, String body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("header", header); m.put("body", body);
        return m;
    }

    private Map<String, Object> buildBarcodeMap(String value) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("type", "QR_CODE"); b.put("value", value); b.put("alternateText", "");
        return b;
    }

    private Barcode buildBarcode(String phone) {
        return new Barcode().setType("QR_CODE").setValue(phone).setAlternateText("");
    }

    // --- MÉTODOS LOYALTY (LEGACY) ---
    private Map<String, Object> buildLoyaltyObjectPayload(String objectId, String classId, Customer customer, String phone, int stamps, String rewardName) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("id", objectId); obj.put("classId", classId); obj.put("state", "ACTIVE");
        obj.put("accountName", resolveAccountName(customer)); obj.put("accountId", phone);
        obj.put("loyaltyPoints", Map.of("label", "Sellos", "balance", Map.of("int", stamps)));
        obj.put("barcode", buildBarcodeMap(phone));
        obj.put("heroImage", Map.of("sourceUri", Map.of("uri", buildHeroImageUrl(stamps, rewardName))));
        obj.put("textModulesData", Collections.singletonList(Map.of("id", "stamp_progress", "header", "Progreso", "body", stamps + "/10")));
        return obj;
    }

    private void syncLoyaltyObject(Walletobjects walletobjects, Customer customer, int stamps, String rewardName) throws Exception {
        String objectId = buildLoyaltyObjectId(customer);
        LoyaltyObject lo = walletobjects.loyaltyobject().get(objectId).execute();
        lo.setLoyaltyPoints(new LoyaltyPoints().setLabel("Sellos").setBalance(new LoyaltyPointsBalance().setInt(stamps)));
        lo.setHeroImage(new Image().setSourceUri(new ImageUri().setUri(buildHeroImageUrl(stamps, rewardName))));
        walletobjects.loyaltyobject().update(objectId, lo).execute();
    }

    private String buildLoyaltyClassId() { return String.format("%s.%s", issuerId, loyaltyClass); }
    private String buildGenericClassId() { return String.format("%s.%s", issuerId, genericClass); }
    private String buildLoyaltyObjectId(Customer customer) { return String.format("%s.%s", issuerId, customer.getPublicId().replace("-", "_")); }
    private String buildGenericObjectId(Customer customer) { return String.format("%s.%s_g", issuerId, customer.getPublicId().replace("-", "_")); }
    private int normalizeStamps(Integer s) { return Math.max(0, s == null ? 0 : s); }

    private String buildHeroImageUrl(int stamps, String rewardName) {
        int bounded = Math.max(0, Math.min(10, stamps));
        String base = heroBaseUrl.endsWith("/") ? heroBaseUrl.substring(0, heroBaseUrl.length() - 1) : heroBaseUrl;
        String encodedReward = URLEncoder.encode(rewardName == null ? DEFAULT_REWARD_NAME : rewardName, StandardCharsets.UTF_8);
        String encodedVersion = URLEncoder.encode(heroVersion == null ? "v1" : heroVersion, StandardCharsets.UTF_8);
        return base + "/hero_dynamic.svg?stamps=" + bounded + "&reward=" + encodedReward + "&v=" + encodedVersion;
    }

    private String normalizePhone(String p) { return (p == null || p.isBlank()) ? "000000000" : p.replace("+", ""); }
    private String resolveAccountName(Customer c) { return (c.getFullName() != null && !c.getFullName().isBlank()) ? c.getFullName() : "Cliente BunnyCure"; }
    private String resolveCurrentRewardName(Customer customer) {
        int rewardIndex = customer.getCurrentRewardIndex() == null ? 0 : customer.getCurrentRewardIndex();
        var reward = loyaltyRewardService.getRewardAt(rewardIndex);
        if (reward == null || reward.getName() == null || reward.getName().isBlank()) {
            return DEFAULT_REWARD_NAME;
        }
        return reward.getName().trim();
    }
    private String normalizedPassType() { return passType == null ? "loyalty" : passType.trim().toLowerCase(Locale.ROOT); }
    private boolean issueGenericPass() { String m = normalizedPassType(); return "generic".equals(m) || "dual".equals(m); }
    private boolean syncLoyaltyPass() { String m = normalizedPassType(); return "loyalty".equals(m) || "dual".equals(m); }
    private boolean syncGenericPass() { String m = normalizedPassType(); return "generic".equals(m) || "dual".equals(m); }
}
