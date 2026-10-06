package cl.duoc.bancoxyz.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Autentica usuarios y entrega tokens para el canal ATM.
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager manager;
    private final JwtService jwt;

    public AuthController(AuthenticationManager manager, JwtService jwt) {
        this.manager = manager;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public Token login(@RequestBody Login request) {
        try {
            Authentication a = manager
                    .authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            String role = a.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            return new Token(jwt.issue(a.getName(), role), "Bearer", role);
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
        }
    }

    public record Login(String username, String password) {
    }

    public record Token(String accessToken, String tokenType, String role) {
    }
}
