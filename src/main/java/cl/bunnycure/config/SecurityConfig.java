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
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final Environment env;
	private final PasswordChangeAuthenticationSuccessHandler passwordChangeSuccessHandler;
	private final CorsConfigurationSource corsConfigurationSource;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		boolean isLocal = Arrays.asList(env.getActiveProfiles()).contains("local");

		// ── CORS ──────────────────────────────────────────────────────────────
		http.cors(cors -> cors.configurationSource(corsConfigurationSource));

		// ── Autorización ──────────────────────────────────────────────────────
		http.authorizeHttpRequests(auth -> {
			// Recursos estáticos y metadatos que no requieren autenticación
			auth.requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll();
			auth.requestMatchers("/favicon.ico", "/.well-known/**").permitAll();
			auth.requestMatchers("/error").permitAll();
			
			// Swagger/OpenAPI documentation (solo en local para desarrollo)
			if (isLocal) {
				auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
			}
			
			// Login
			auth.requestMatchers("/login", "/login/**").permitAll();
			auth.requestMatchers("/forgot-password", "/reset-password").permitAll();
			
			// Cambio de contraseña (requiere autenticación pero no puede ser bloqueado)
			auth.requestMatchers("/admin/change-password").authenticated();
			
			// Admin section: require ADMIN role
			auth.requestMatchers("/admin/**").hasRole("ADMIN");
			
			// Portal público: GET y POST de reservas
			auth.requestMatchers("/", "/reservar", "/reservar/**", "/reservar/submit").permitAll();
			
			// API pública: búsqueda de clientes por teléfono
			auth.requestMatchers("/api/customers/lookup").permitAll();
			
			// API pública: servicios (para portal de reservas)
			auth.requestMatchers(HttpMethod.GET, "/api/services").permitAll();
			
			// API REST endpoints (requieren autenticación)
			auth.requestMatchers("/api/auth/**").authenticated(); // endpoints de autenticación
			auth.requestMatchers("/api/appointments/**").authenticated();
			auth.requestMatchers("/api/customers/**").authenticated(); // excepto /lookup que ya está permitido arriba
			auth.requestMatchers("/api/services/**").authenticated(); // excepto GET /services que ya está permitido arriba
			auth.requestMatchers("/api/booking-requests/**").authenticated();
			
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
					.ignoringRequestMatchers("/h2-console/**", "/", "/reservar", "/reservar/**", "/reservar/submit", "/api/**", "/login", "/logout")
			);
			http.headers(headers -> headers
					.frameOptions(frame -> frame.sameOrigin())
			);
		} else {
			// Disable CSRF for public booking portal and API endpoints
			http.csrf(csrf -> csrf
					.ignoringRequestMatchers("/", "/reservar", "/reservar/**", "/reservar/submit", "/api/**", "/login", "/logout")
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