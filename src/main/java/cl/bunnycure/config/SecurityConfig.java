package cl.bunnycure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	private final Environment env;

	@Value("${bunnycure.admin.username:admin}")
	private String adminUsername;

	@Value("${bunnycure.admin.password:changeme}")
	private String adminPassword;

	public SecurityConfig(Environment env) {
		this.env = env;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		boolean isLocal = Arrays.asList(env.getActiveProfiles()).contains("local");

		// ── Autorización ──────────────────────────────────────────────────────
		http.authorizeHttpRequests(auth -> {
			auth.requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll();
			auth.requestMatchers("/reservar/**","/login", "/login/**").permitAll();

			if (isLocal) {
				auth.requestMatchers("/h2-console/**").permitAll();
			}

			auth.anyRequest().authenticated();
		});

		// ── Login ─────────────────────────────────────────────────────────────
		http.formLogin(form -> form
				.loginPage("/login")
				.loginProcessingUrl("/login")
				.defaultSuccessUrl("/dashboard", true)
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
			// H2 Console necesita iframes y POST sin CSRF token
			http.csrf(csrf -> csrf
					.ignoringRequestMatchers("/h2-console/**")
			);
			http.headers(headers -> headers
					.frameOptions(frame -> frame.sameOrigin())
			);
		} else {
			// Producción: headers estrictos
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
	// ⚠️ PRODUCCIÓN: migrar a UserDetailsService con tabla de base de datos
	@Bean
	public InMemoryUserDetailsManager userDetailsManager(PasswordEncoder encoder) {
		log.info(">>> Configurando usuario administrador: '{}'", adminUsername);

		var admin = User.builder()
				.username(adminUsername)
				.password(encoder.encode(adminPassword))
				.roles("ADMIN")
				.build();

		return new InMemoryUserDetailsManager(admin);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}