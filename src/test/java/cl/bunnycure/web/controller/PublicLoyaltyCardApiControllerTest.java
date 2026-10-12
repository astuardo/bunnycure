package cl.bunnycure.web.controller;

import cl.bunnycure.exception.GlobalExceptionHandler;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.service.CustomerService;
import cl.bunnycure.web.dto.LoyaltyCardPublicDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicLoyaltyCardApiControllerTest {

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private PublicLoyaltyCardApiController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getLoyaltyCard_WhenFound_Returns200WithCardData() throws Exception {
        LoyaltyCardPublicDto dto = LoyaltyCardPublicDto.builder()
                .publicId("test-uuid")
                .customerName("Camila Silva")
                .firstName("Camila")
                .phone("+569 •••• 1234")
                .loyaltyStamps(6)
                .maxStamps(10)
                .stampsRemaining(4)
                .progressPercent(60)
                .currentRewardName("Esmaltado Permanente")
                .rewardAvailable(false)
                .rewardStatusMessage("Te faltan 4 visitas para ganar: Esmaltado Permanente")
                .qrValue("https://bunnycure.cl/mi-tarjeta/test-uuid")
                .salonName("BunnyCure")
                .build();

        when(customerService.getPublicLoyaltyCard("test-uuid")).thenReturn(dto);

        mockMvc.perform(get("/api/public/loyalty-card/test-uuid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").value("test-uuid"))
                .andExpect(jsonPath("$.data.customerName").value("Camila Silva"))
                .andExpect(jsonPath("$.data.firstName").value("Camila"))
                .andExpect(jsonPath("$.data.loyaltyStamps").value(6))
                .andExpect(jsonPath("$.data.stampsRemaining").value(4))
                .andExpect(jsonPath("$.data.progressPercent").value(60))
                .andExpect(jsonPath("$.data.currentRewardName").value("Esmaltado Permanente"))
                .andExpect(jsonPath("$.data.rewardAvailable").value(false));
    }

    @Test
    void getLoyaltyCard_WhenNotFound_Returns404() throws Exception {
        when(customerService.getPublicLoyaltyCard("non-existent"))
                .thenThrow(new ResourceNotFoundException("Cliente no encontrado con identificador: non-existent"));

        mockMvc.perform(get("/api/public/loyalty-card/non-existent")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
