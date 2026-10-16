package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.service.WebPushNotificationService;
import cl.bunnycure.web.dto.WebPushSubscriptionRequestDto;
import cl.bunnycure.web.dto.WebPushSubscriptionResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebPushSubscriptionApiControllerTest {

    @Mock
    private WebPushNotificationService webPushNotificationService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WebPushSubscriptionApiController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    private static final Long USER_ID = 7L;
    private static final TestingAuthenticationToken AUTH = new TestingAuthenticationToken("push-owner", null);

    private void currentUserExists() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("push-owner");
        when(userRepository.findByUsername("push-owner")).thenReturn(Optional.of(user));
    }

    @Test
    void getPublicKey_ReturnsConfiguredPublicKey() throws Exception {
        when(webPushNotificationService.getPublicKey()).thenReturn("test-vapid-public-key-xyz");

        mockMvc.perform(get("/api/push-subscriptions/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicKey").value("test-vapid-public-key-xyz"));

        verify(webPushNotificationService, times(1)).getPublicKey();
    }

    @Test
    void register_SavesValidSubscription() throws Exception {
        WebPushSubscriptionRequestDto request = new WebPushSubscriptionRequestDto();
        request.setEndpoint("https://updates.push.services.mozilla.com/wpush/v2/gAAAAAB");
        WebPushSubscriptionRequestDto.Keys keys = new WebPushSubscriptionRequestDto.Keys();
        keys.setP256dh("p256dh-key");
        keys.setAuth("auth-secret");
        request.setKeys(keys);

        WebPushSubscriptionResponseDto responseDto = WebPushSubscriptionResponseDto.builder()
                .id(10L)
                .endpoint(request.getEndpoint())
                .active(true)
                .build();

        currentUserExists();
        when(webPushNotificationService.saveSubscription(any(WebPushSubscriptionRequestDto.class), eq(USER_ID)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/push-subscriptions").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.active").value(true));

        verify(webPushNotificationService, times(1)).saveSubscription(any(WebPushSubscriptionRequestDto.class), eq(USER_ID));
    }

    @Test
    void unsubscribe_DeactivatesSubscription() throws Exception {
        currentUserExists();
        mockMvc.perform(post("/api/push-subscriptions/unsubscribe").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example.com/sub/123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Suscripción eliminada"));

        verify(webPushNotificationService, times(1)).deactivateSubscription("https://push.example.com/sub/123", USER_ID);
    }

    @Test
    void sendTest_DispatchesTestPushNotification() throws Exception {
        currentUserExists();
        when(webPushNotificationService.sendTestNotification("https://push.example.com/sub/123", USER_ID)).thenReturn(true);

        mockMvc.perform(post("/api/push-subscriptions/test").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example.com/sub/123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true));

        verify(webPushNotificationService, times(1)).sendTestNotification("https://push.example.com/sub/123", USER_ID);
    }

    @Test
    void debug_ReturnsDiagnostics() throws Exception {
        when(webPushNotificationService.getDiagnostics()).thenReturn(Map.of("webPushEnabled", true));

        mockMvc.perform(get("/api/push-subscriptions/debug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.webPushEnabled").value(true));
    }
}
