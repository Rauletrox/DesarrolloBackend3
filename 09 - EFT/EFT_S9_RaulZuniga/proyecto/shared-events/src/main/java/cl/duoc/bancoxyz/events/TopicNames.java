package cl.duoc.bancoxyz.events;

// Centraliza los nombres de los topics utilizados por Kafka.
public final class TopicNames {
    public static final String WITHDRAWAL_REQUESTED = "bank.withdrawal.requested";
    public static final String WITHDRAWAL_COMPLETED = "bank.withdrawal.completed";
    public static final String WITHDRAWAL_REJECTED = "bank.withdrawal.rejected";
    public static final String TRANSACTIONS_COMPLETED = "bank.transactions.completed";
    public static final String SECURITY_ALERTS = "bank.security.alerts";

    private TopicNames() {
    }
}
