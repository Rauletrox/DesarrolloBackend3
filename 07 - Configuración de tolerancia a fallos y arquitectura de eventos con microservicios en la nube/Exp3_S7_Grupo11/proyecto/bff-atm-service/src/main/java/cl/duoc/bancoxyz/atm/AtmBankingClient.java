package cl.duoc.bancoxyz.atm;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

// Protege las consultas ATM al Core ante fallos temporales.
@Service
public class AtmBankingClient {
    private final RestClient rest;
    private final CircuitBreakerFactory<?, ?> breakers;

    public AtmBankingClient(RestClient.Builder b, CircuitBreakerFactory<?, ?> breakers) {
        rest = b.baseUrl("http://core-banking-service").build();
        this.breakers = breakers;
    }

    public Balance balance(Integer id) {
        return breakers.create("coreAtm")
                .run(() -> rest.get().uri("/api/core/accounts/{id}/balance", id).retrieve().body(Balance.class), e -> {
                    throw unavailable();
                });
    }

    public AsyncWithdrawalStatus asyncStatus(UUID transactionId) {
        return breakers.create("coreAtm").run(() -> rest.get()
                .uri("/api/core/accounts/async-withdrawals/{transactionId}", transactionId)
                .retrieve().body(AsyncWithdrawalStatus.class), e -> { throw unavailable(); });
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Servicio bancario temporalmente no disponible");
    }

    public record Balance(Integer accountId, BigDecimal balance) {
    }

    public record AsyncWithdrawalStatus(UUID transactionId, Integer accountId, BigDecimal amount, String status,
            BigDecimal finalBalance, String rejectionReason, java.time.Instant updatedAt) {
    }
}
