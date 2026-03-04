package cl.bunnycure.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Test configuration for Spring Mail
 * Provides a mock JavaMailSender for testing without actual mail server
 */
@TestConfiguration
public class TestMailConfiguration {

    @Bean
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl();
    }
}
