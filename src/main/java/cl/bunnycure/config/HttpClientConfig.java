package cl.bunnycure.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean(name = "apiGatewayRestTemplate")
    public RestTemplate apiGatewayRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(90))
                .build();
    }

    @Bean
    @Primary
    public RestTemplate generalRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(90))
                .build();
    }
}

