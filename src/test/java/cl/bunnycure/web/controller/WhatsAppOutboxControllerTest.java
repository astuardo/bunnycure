package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.OutboxMessageType;
import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.service.WhatsAppOutboxService;
import cl.bunnycure.web.dto.WhatsAppOutboxMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WhatsAppOutboxControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WhatsAppOutboxService outboxService;

    @InjectMocks
    private WhatsAppOutboxController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(mapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void list_ReturnsPagedMessages() throws Exception {
        WhatsAppOutboxMessageDto dto = WhatsAppOutboxMessageDto.builder()
                .id(1L)
                .recipientPhone("56999792546")
                .templateName("confirmacion_cita")
                .messageType(OutboxMessageType.TEMPLATE)
                .status(OutboxStatus.FAILED)
                .attemptCount(1)
                .lastError("Template does not exist")
                .build();

        Page<WhatsAppOutboxMessageDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 15), 1);
        when(outboxService.getMessages(any(), anyBoolean())).thenReturn(page);

        mockMvc.perform(get("/api/whatsapp/outbox?page=0&size=15&pendingOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].recipientPhone").value("56999792546"))
                .andExpect(jsonPath("$.data.content[0].templateName").value("confirmacion_cita"))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"));
    }

    @Test
    void getPendingCount_ReturnsCount() throws Exception {
        when(outboxService.getPendingCount()).thenReturn(3L);

        mockMvc.perform(get("/api/whatsapp/outbox/pending-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pendingCount").value(3));
    }

    @Test
    void retryMessage_ReturnsResult() throws Exception {
        when(outboxService.retryMessage(1L)).thenReturn(Map.of("id", 1L, "success", true, "message", "Enviado"));

        mockMvc.perform(post("/api/whatsapp/outbox/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void retryAll_ReturnsSummary() throws Exception {
        when(outboxService.retryAll()).thenReturn(Map.of("total", 2, "succeeded", 2, "failed", 0));

        mockMvc.perform(post("/api/whatsapp/outbox/retry-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.succeeded").value(2));
    }

    @Test
    void discardMessage_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/whatsapp/outbox/1/discard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discarded").value(true));

        verify(outboxService).discardMessage(1L);
    }

    @Test
    void discardAll_ReturnsCount() throws Exception {
        when(outboxService.discardAll()).thenReturn(4);

        mockMvc.perform(post("/api/whatsapp/outbox/discard-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discardedCount").value(4));
    }
}
