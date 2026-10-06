package cl.duoc.bancoxyz.atm.kafka;

import cl.duoc.bancoxyz.events.TopicNames;
import cl.duoc.bancoxyz.events.WithdrawalRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AsyncWithdrawalProducer {
    private static final Logger log = LoggerFactory.getLogger(AsyncWithdrawalProducer.class);
    private final KafkaTemplate<String, Object> kafka;

    public AsyncWithdrawalProducer(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    // Usa la cuenta como clave para conservar el orden de sus retiros.
    public void publish(WithdrawalRequestedEvent event) {
        kafka.send(TopicNames.WITHDRAWAL_REQUESTED, event.accountId().toString(), event)
                .whenComplete((result, error) -> {
                    if (error == null) {
                        log.info("[Kafka][Producer] WithdrawalRequested eventId={} transactionId={}",
                                event.eventId(), event.transactionId());
                    } else {
                        log.error("[Kafka][Producer] No fue posible publicar transactionId={}",
                                event.transactionId(), error);
                    }
                });
    }
}
