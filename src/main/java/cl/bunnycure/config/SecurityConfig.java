package cl.bunnycure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	private final Environment env;
	private final CorsConfigurationSource corsConfigurationSource;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final PwaRedirectFilter pwaRedirectFilter;

	public SecurityConfig(
			Environment env,
			CorsConfigurationSource corsConfigurationSource,
			RestAuthenticationEntryPoint restAuthenticationEntryPoint,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			PwaRedirectFilter pwaRedirectFilter) {
		this.env = env;
		this.corsConfigurationSource = corsConfigurationSource;
		this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.pwaRedirectFilter = pwaRedirectFilter;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		boolean isLocal = Arrays.asList(env.getActiveProfiles()).contains("local");

		// ── CORS ──────────────────────────────────────────────────────────────
		http.cors(cors -> cors.configurationSource(corsConfigurationSource));

		// ── Autorización ──────────────────────────────────────────────────────
		http.authorizeHttpRequests(auth -> {
			// Recursos estáticos y metadatos que no requieren autenticación
			auth.requestMatchers("/css/**", "/js/**", "/images/**", "/assets/**", "/webjars/**").permitAll();
			auth.requestMatchers("/favicon.ico", "/.well-known/**").permitAll();
			auth.requestMatchers("/error").permitAll();
			
			// Swagger/OpenAPI documentation (solo en local para desarrollo)
			if (isLocal) {
				auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
			}
			
			// Rutas públicas de vistas (manejadas por PwaRedirectFilter hacia la PWA)
			auth.requestMatchers("/", "/reservar", "/reservar/**", "/login", "/forgot-password", "/reset-password").permitAll();
			auth.requestMatchers("/admin/**", "/dashboard/**", "/appointments/**", "/customers/**").permitAll();
			
			// API pública: búsqueda de clientes por teléfono y gift cards
			auth.requestMatchers("/api/customers/lookup").permitAll();
			auth.requestMatchers("/api/public/**").permitAll();
			auth.requestMatchers("/w/**", "/tarjeta/**", "/pass/**", "/mi-tarjeta/**").permitAll();
			
			// API pública: servicios (para portal de reservas)
			auth.requestMatchers(HttpMethod.GET, "/api/services").permitAll();
			
			// API de autenticación pública
			auth.requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/refresh").permitAll();
			auth.requestMatchers("/api/auth/csrf").permitAll();
			
			// Notificaciones Web Push (VAPID)
			auth.requestMatchers("/api/push-subscriptions/**").permitAll();
			
			// API REST endpoints protegidos por RBAC
			auth.requestMatchers("/api/auth/**").authenticated();
			auth.requestMatchers("/api/admin/tenants/**").hasAnyRole("SUPER_ADMIN", "ADMIN");
			auth.requestMatchers("/api/users/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN");
			auth.requestMatchers("/api/settings/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN");
			auth.requestMatchers("/api/inventory/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN");
			auth.requestMatchers("/api/booking-requests/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN", "RECEPTIONIST");
			auth.requestMatchers("/api/reminders/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN", "RECEPTIONIST");
			auth.requestMatchers("/api/marketing/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN");
			auth.requestMatchers("/api/loyalty-rewards/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN", "RECEPTIONIST");
			auth.requestMatchers("/api/stats/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN", "RECEPTIONIST");
			auth.requestMatchers("/api/invoices/**").hasAnyRole("SUPER_ADMIN", "SALON_ADMIN", "ADMIN", "RECEPTIONIST");
			auth.requestMatchers("/api/appointments/**").authenticated();
			auth.requestMatchers("/api/customers/**").authenticated();
			auth.requestMatchers("/api/services/**").authenticated();
			auth.requestMatchers("/api/whatsapp/**").authenticated();
			
			// Webhook de WhatsApp (público para Meta: soporta plural /webhooks/ y singular /webhook/)
			auth.requestMatchers(HttpMethod.GET, "/api/webhooks/whatsapp", "/api/webhooks/whatsapp/**", "/api/webhook/whatsapp", "/api/webhook/whatsapp/**").permitAll();
			auth.requestMatchers(HttpMethod.POST, "/api/webhooks/whatsapp", "/api/webhooks/whatsapp/**", "/api/webhook/whatsapp", "/api/webhook/whatsapp/**").permitAll();
			auth.requestMatchers("/api/webhooks/whatsapp/status", "/api/webhook/whatsapp/status").permitAll();
			
			// API de pruebas WhatsApp (solo en local)
			if (isLocal) {
				auth.requestMatchers("/api/test/**").permitAll();
				auth.requestMatchers("/api/webhooks/whatsapp/test", "/api/webhook/whatsapp/test").permitAll();
			}

			if (isLocal) {
				auth.requestMatchers("/h2-console/**").permitAll();
			}

			auth.anyRequest().authenticated();
		});

		// ── Sesiones: Stateless para API REST desacoplada ──────────────────────
		http.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
		);

		// ── Exception Handling ────────────────────────────────────────────────
		// Usar custom entry point para retornar JSON 401 en APIs en vez de redirect HTML
		http.exceptionHandling(ex -> ex
				.authenticationEntryPoint(restAuthenticationEntryPoint)
		);

		// ── PWA Redirect Filter & JWT Filter ───────────────────────────────────
		// Redirecciona vistas legadas del monolito a la PWA con 301
		http.addFilterBefore(pwaRedirectFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
		// Autenticación Stateless vía JWT Bearer
		http.addFilterBefore(jwtAuthenticationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

		// ── CSRF: Desactivado para API REST Stateless ──────────────────────────
		http.csrf(csrf -> csrf.disable());

		if (isLocal) {
			http.headers(headers -> headers
					.frameOptions(frame -> frame.sameOrigin())
			);
		} else {
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

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
		return authConfig.getAuthenticationManager();
	}
}
