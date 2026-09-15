package com.backen3.Duoc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ChannelAuthInterceptor implements HandlerInterceptor {

    private final Map<String, String> tokensByChannel;

    public ChannelAuthInterceptor(
            @Value("${app.security.tokens.web}") String webToken,
            @Value("${app.security.tokens.mobile}") String mobileToken,
            @Value("${app.security.tokens.atm}") String atmToken) {
        this.tokensByChannel = Map.of(
                "web", webToken,
                "mobile", mobileToken,
                "atm", atmToken);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String channel = resolveChannel(request.getRequestURI());
        if (channel == null) {
            return true;
        }

        String expectedToken = tokensByChannel.get(channel);
        String providedToken = request.getHeader("X-Channel-Token");

        if (expectedToken.equals(providedToken)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error":"No autorizado","detalle":"Token de canal invalido o ausente"}
                """);
        return false;
    }

    private String resolveChannel(String uri) {
        if (uri.startsWith("/api/bff/web/")) {
            return "web";
        }
        if (uri.startsWith("/api/bff/mobile/")) {
            return "mobile";
        }
        if (uri.startsWith("/api/bff/atm/")) {
            return "atm";
        }
        return null;
    }
}
