package cl.duoc.bancoxyz.web;

import java.math.BigDecimal;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

// Protege las consultas web al Core ante fallos temporales.
@Service
public class WebBankingClient {
    private final RestClient rest;
    private final CircuitBreakerFactory<?, ?> breakers;

    public WebBankingClient(RestClient.Builder builder, CircuitBreakerFactory<?, ?> breakers) {
        this.rest = builder.baseUrl("http://core-banking-service:8081").build();
        this.breakers = breakers;
    }

    public WebAccount account(Integer id) {
        return breakers.create("coreWeb")
                .run(() -> rest.get().uri("/api/core/accounts/{id}", id).retrieve().body(WebAccount.class), e -> {
                    throw unavailable();
                });
    }

    public AnnualStatement statement(Integer id) {
        return breakers.create("coreWeb").run(() -> rest.get().uri("/api/core/accounts/{id}/annual-statement", id)
                .retrieve().body(AnnualStatement.class), e -> {
                    throw unavailable();
                });
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Servicio bancario temporalmente no disponible");
    }

    public record WebAccount(Integer accountId, String accountName, String accountType, BigDecimal initialBalance,
            BigDecimal interestRate, BigDecimal finalBalance) {
    }

    public record AnnualStatement(Integer accountId, int movementCount, BigDecimal totalDeposits,
            BigDecimal totalCharges, BigDecimal netBalance) {
    }
}
