package integrationhub.job;

import integrationhub.domain.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Uma unidade de sincronização, com histórico de tentativas para o painel e o reprocessamento. */
@Entity
@Table(name = "integration_jobs")
public class IntegrationJob {

    public enum Type {
        ORDER_TO_ERP("orders"), STOCK_TO_MARKETPLACE("stock");

        private final String routingKey;

        Type(String routingKey) {
            this.routingKey = routingKey;
        }

        public String routingKey() {
            return routingKey;
        }
    }

    public enum Status { PENDING, DONE, FAILED }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    /** Id do pedido no marketplace, ou id da rodada de estoque. Único por tipo e canal. */
    @Column(nullable = false, length = 80)
    private String reference;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(length = 120)
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected IntegrationJob() {
    }

    IntegrationJob(Type type, Channel channel, String reference, String payload, Instant now) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.channel = channel;
        this.reference = reference;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void succeeded(String result, Instant now) {
        this.status = Status.DONE;
        this.result = result;
        this.lastError = null;
        this.updatedAt = now;
    }

    /** @return true se ainda há tentativas; false se o job foi para FAILED */
    boolean failed(String error, int maxAttempts, boolean retryable, Instant now) {
        attempts++;
        lastError = error == null ? "erro sem mensagem" : error.substring(0, Math.min(500, error.length()));
        updatedAt = now;
        if (!retryable || attempts >= maxAttempts) {
            status = Status.FAILED;
            return false;
        }
        return true;
    }

    void reset(Instant now) {
        status = Status.PENDING;
        attempts = 0;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public Type getType() { return type; }
    public Channel getChannel() { return channel; }
    public String getReference() { return reference; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public String getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
