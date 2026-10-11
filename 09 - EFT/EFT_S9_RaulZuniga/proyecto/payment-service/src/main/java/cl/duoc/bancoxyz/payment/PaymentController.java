package cl.duoc.bancoxyz.payment;

import cl.duoc.bancoxyz.events.TopicNames;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public PaymentController(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @PostMapping("/transfer")
  public PaymentResponse transfer(@RequestBody PaymentRequest request) {
    PaymentResponse response = new PaymentResponse(
        UUID.randomUUID().toString(),
        request.sourceAccountId(),
        request.targetAccountId(),
        request.amount(),
        "COMPLETED",
        Instant.now());
    kafkaTemplate.send(TopicNames.TRANSACTIONS_COMPLETED, response.transactionId(), response);
    return response;
  }

  @PostMapping("/deposit")
  public PaymentResponse deposit(@RequestBody DepositRequest request) {
    PaymentResponse response = new PaymentResponse(
        UUID.randomUUID().toString(),
        "CASH",
        request.accountId(),
        request.amount(),
        "COMPLETED",
        Instant.now());
    kafkaTemplate.send(TopicNames.TRANSACTIONS_COMPLETED, response.transactionId(), response);
    return response;
  }

  public record PaymentRequest(String sourceAccountId, String targetAccountId, BigDecimal amount) {
  }

  public record DepositRequest(String accountId, BigDecimal amount) {
  }

  public record PaymentResponse(
      String transactionId,
      String sourceAccountId,
      String targetAccountId,
      BigDecimal amount,
      String status,
      Instant completedAt) {
  }
}
