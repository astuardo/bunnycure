package cl.bunnycure.web.controller;

import cl.bunnycure.service.IncomingWhatsAppMessageService;
import cl.bunnycure.web.dto.IncomingWhatsAppMessageDto;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IncomingWhatsAppMessageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IncomingWhatsAppMessageService messageService;

    @InjectMocks
    private IncomingWhatsAppMessageController controller;

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
    void getMessages_ReturnsPagedDto() throws Exception {
        IncomingWhatsAppMessageDto dto = IncomingWhatsAppMessageDto.builder()
                .id(1L)
                .fromPhone("56987654321")
                .senderName("Vale")
                .content("Fui el dia 14")
                .messageType("text")
                .isRead(false)
                .replyUrl("https://wa.me/56987654321")
                .createdAt(LocalDateTime.now())
                .build();

        Page<IncomingWhatsAppMessageDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);
        when(messageService.getMessages(anyInt(), anyInt(), anyBoolean(), anyBoolean())).thenReturn(page);

        mockMvc.perform(get("/api/whatsapp/messages?page=0&size=10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].senderName").value("Vale"))
                .andExpect(jsonPath("$.data.content[0].content").value("Fui el dia 14"))
                .andExpect(jsonPath("$.data.content[0].replyUrl").value("https://wa.me/56987654321"));
    }

    @Test
    void getUnreadCount_ReturnsNumber() throws Exception {
        when(messageService.getUnreadCount()).thenReturn(5L);

        mockMvc.perform(get("/api/whatsapp/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(5));
    }

    @Test
    void markAsRead_ReturnsOk() throws Exception {
        when(messageService.markAsRead(10L)).thenReturn(true);

        mockMvc.perform(patch("/api/whatsapp/messages/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void markByPhoneAsRead_ReturnsOk() throws Exception {
        when(messageService.markByPhoneAsRead("56987654321")).thenReturn(3);

        mockMvc.perform(patch("/api/whatsapp/messages/by-phone/56987654321/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedCount").value(3));
    }
}
