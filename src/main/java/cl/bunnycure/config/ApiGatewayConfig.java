package cl.bunnycure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class ApiGatewayConfig {

    @Value("${apigateway.token:}")
    private String token;

    @Value("${apigateway.endpoint:https://app.apigateway.cl}")
    private String endpoint;

    @Value("${apigateway.enabled:false}")
    private boolean enabled;

    @Value("${apigateway.sii.rut:}")
    private String siiRut;

    @Value("${apigateway.sii.password:}")
    private String siiPassword;

    @Value("${apigateway.send-email-on-issue:true}")
    private boolean sendEmailOnIssue;

    public boolean isConfigured() {
        return enabled && token != null && !token.isBlank() 
                && siiRut != null && !siiRut.isBlank() 
                && siiPassword != null && !siiPassword.isBlank();
    }
}
