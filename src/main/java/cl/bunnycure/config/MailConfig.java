package cl.bunnycure.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfig {
	// Mail timeout configuration is handled via application.properties
	// Spring Boot automatically applies these settings:
	// - spring.mail.properties.mail.smtp.connectiontimeout=30000
	// - spring.mail.properties.mail.smtp.timeout=30000
	// - spring.mail.properties.mail.smtp.writetimeout=30000
}


