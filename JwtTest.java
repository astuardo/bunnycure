import java.util.*;

public class JwtTest {
    public static void main(String[] args) {
        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", "test@test.com");
            claims.put("aud", "google");
            claims.put("typ", "savetowallet");
            claims.put("iat", 1234567890L);
            claims.put("origins", Arrays.asList("http://localhost:5173"));
            
            Map<String, Object> loyaltyObject = new LinkedHashMap<>();
            loyaltyObject.put("id", "3388000000023103816.ID_PRUEBA_001");
            loyaltyObject.put("classId", "3388000000023103816.bunnycure_loyalty_card");
            loyaltyObject.put("state", "ACTIVE");
            loyaltyObject.put("accountName", "Cliente de Prueba");
            loyaltyObject.put("accountId", "56912345678");
            
            Map<String, Object> loyaltyPoints = new LinkedHashMap<>();
            Map<String, Object> balance = new LinkedHashMap<>();
            balance.put("int", 5);
            loyaltyPoints.put("balance", balance);
            loyaltyPoints.put("label", "Sellos");
            loyaltyObject.put("loyaltyPoints", loyaltyPoints);

            Map<String, Object> barcode = new LinkedHashMap<>();
            barcode.put("type", "QR_CODE");
            barcode.put("value", "56912345678");
            barcode.put("alternateText", "56912345678");
            loyaltyObject.put("barcode", barcode);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loyaltyObjects", Collections.singletonList(loyaltyObject));
            claims.put("payload", payload);
            
            // To see the json representation, we will just print the claims map since jjwt requires a private key to sign
            // Wait, we can't print the raw map as a nice JSON without a serializer. Let's use jackson.
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(claims));

        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
