package cl.duoc.bancoxyz.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Emite y valida los tokens JWT del canal mobile.
@Service
public class JwtService {
    private final Key key;

    public JwtService(@Value("${security.jwt.secret}") String secret) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(username).claim("role", role).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600))).signWith(key).compact();
    }

    public Claims claims(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build().parseSignedClaims(token).getPayload();
    }
}
