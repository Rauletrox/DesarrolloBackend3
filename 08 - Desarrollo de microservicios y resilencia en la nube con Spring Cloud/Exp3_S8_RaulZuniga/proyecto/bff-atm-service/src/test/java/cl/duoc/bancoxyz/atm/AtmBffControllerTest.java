package cl.duoc.bancoxyz.atm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cl.duoc.bancoxyz.atm.kafka.AsyncWithdrawalProducer;
import cl.duoc.bancoxyz.events.WithdrawalRequestedEvent;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AtmBffControllerTest {

    @Test
    void withdrawPublishesKafkaEventAndAcceptsPendingTransaction() {
        AsyncWithdrawalProducer producer = mock(AsyncWithdrawalProducer.class);
        AtmBffController controller = new AtmBffController(mock(AtmBankingClient.class), producer);

        ResponseEntity<AtmBffController.AsyncWithdrawalAccepted> response = controller.withdraw(104,
                new AtmBffController.WithdrawRequest(new BigDecimal("10000")));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody().transactionId());
        assertEquals("PENDING", response.getBody().status());
        ArgumentCaptor<WithdrawalRequestedEvent> event = ArgumentCaptor.forClass(WithdrawalRequestedEvent.class);
        verify(producer).publish(event.capture());
        assertNotNull(event.getValue().eventId());
        assertEquals(response.getBody().transactionId(), event.getValue().transactionId());
        assertEquals(104, event.getValue().accountId());
        assertEquals(new BigDecimal("10000"), event.getValue().amount());
    }
}
