package cl.bunnycure.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimpleApiInvoiceRequest {
    private String rut;
    private String nombre;
    private String email;
    private BigDecimal montoBruto;
    private String descripcion;
    private String fechaEmision;
    private String moneda;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class SimpleApiInvoiceResponse {
    private boolean success;
    private String message;
    private String transactionId;
    private String invoiceNumber;
    private String error;
    private Object data;
}
