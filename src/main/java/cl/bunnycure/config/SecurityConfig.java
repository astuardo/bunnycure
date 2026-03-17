package cl.bunnycure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final Environment env;
	private final PasswordChangeAuthenticationSuccessHandler passwordChangeSuccessHandler;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		boolean isLocal = Arrays.asList(env.getActiveProfiles()).contains("local");

		// ── Autorización ──────────────────────────────────────────────────────
		http.authorizeHttpRequests(auth -> {
			// Recursos estáticos y metadatos que no requieren autenticación
			auth.requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll();
			auth.requestMatchers("/favicon.ico", "/.well-known/**").permitAll();
			auth.requestMatchers("/error").permitAll();
			
			// Login
			auth.requestMatchers("/login", "/login/**").permitAll();
			auth.requestMatchers("/forgot-password", "/reset-password").permitAll();
			
			// Cambio de contraseña (requiere autenticación pero no puede ser bloqueado)
			auth.requestMatchers("/admin/change-password").authenticated();
			
			// Portal público: GET y POST de reservas
			auth.requestMatchers("/", "/reservar", "/reservar/**", "/reservar/submit").permitAll();
			
			// API pública: búsqueda de clientes por teléfono
			auth.requestMatchers("/api/customers/lookup").permitAll();
			
			// Webhook de WhatsApp (solo endpoint oficial público)
			auth.requestMatchers(HttpMethod.GET, "/api/webhooks/whatsapp").permitAll();
			auth.requestMatchers(HttpMethod.POST, "/api/webhooks/whatsapp").permitAll();
			
			// API de pruebas WhatsApp (solo en local)
			if (isLocal) {
				auth.requestMatchers("/api/test/**").permitAll();
				auth.requestMatchers("/api/webhooks/whatsapp/test", "/api/webhooks/whatsapp/status").permitAll();
			}

			if (isLocal) {
				auth.requestMatchers("/h2-console/**").permitAll();
			}

			auth.anyRequest().authenticated();
		});

		// ── Sesiones: crear solo cuando sea necesario ──────────────────────────
		http.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
		);

		// ── Login ─────────────────────────────────────────────────────────────
		http.formLogin(form -> form
				.loginPage("/login")
				.loginProcessingUrl("/login")
				.successHandler(passwordChangeSuccessHandler)
				.failureUrl("/login?error=true")
				.usernameParameter("username")
				.passwordParameter("password")
				.permitAll()
		);

		// ── Logout ────────────────────────────────────────────────────────────
		http.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessUrl("/login?logout=true")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
				.permitAll()
		);

		// ── Headers según perfil ──────────────────────────────────────────────
		if (isLocal) {
			http.csrf(csrf -> csrf
					.ignoringRequestMatchers("/h2-console/**", "/", "/reservar", "/reservar/**", "/reservar/submit", "/api/customers/lookup", "/api/test/**", "/api/webhooks/whatsapp")
			);
			http.headers(headers -> headers
					.frameOptions(frame -> frame.sameOrigin())
			);
		} else {
			// Disable CSRF for public booking portal to avoid session creation issues
			http.csrf(csrf -> csrf
					.ignoringRequestMatchers("/", "/reservar", "/reservar/**", "/reservar/submit", "/api/customers/lookup", "/api/webhooks/whatsapp")
			);
			http.headers(headers -> headers
					.frameOptions(frame -> frame.deny())
					.httpStrictTransportSecurity(hsts -> hsts
							.includeSubDomains(true)
							.maxAgeInSeconds(31536000)
					)
			);
		}

		return http.build();
	}

	// ── UserDetails ───────────────────────────────────────────────────────────
	// Nota: UserDetailsService se obtiene automáticamente desde UserService
	// que implementa UserDetailsService y carga usuarios desde BD

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}