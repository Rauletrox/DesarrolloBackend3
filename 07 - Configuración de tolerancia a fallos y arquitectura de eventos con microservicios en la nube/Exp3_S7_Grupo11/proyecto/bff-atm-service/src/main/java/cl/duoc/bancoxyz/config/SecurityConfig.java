package cl.duoc.bancoxyz.config;

import cl.duoc.bancoxyz.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Protege los recursos del canal ATM con JWT y su rol.
@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder, @Value("${security.users.web.password}") String web,
            @Value("${security.users.mobile.password}") String mobile,
            @Value("${security.users.atm.password}") String atm) {
        return new InMemoryUserDetailsManager(
                User.withUsername("webuser").password(encoder.encode(web)).roles("WEB").build(),
                User.withUsername("mobileuser").password(encoder.encode(mobile)).roles("MOBILE").build(),
                User.withUsername("atmuser").password(encoder.encode(atm)).roles("ATM").build());
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(encoder);
        return provider::authenticate;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwt,
            @Value("${security.channel-role}") String role) throws Exception {
        return http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/auth/login", "/actuator/health").permitAll().anyRequest().hasRole(role))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(
                                (q, r, x) -> json(r, 401, "UNAUTHORIZED", "Token JWT requerido o invalido"))
                        .accessDeniedHandler((q, r, x) -> json(r, 403, "FORBIDDEN",
                                "Usuario autenticado sin permisos para este canal")))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
    }

    private static void json(jakarta.servlet.http.HttpServletResponse r, int status, String error, String message)
            throws java.io.IOException {
        r.setStatus(status);
        r.setContentType("application/json");
        new ObjectMapper().writeValue(r.getOutputStream(),
                Map.of("status", status, "error", error, "message", message));
    }
}
