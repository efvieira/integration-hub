package integrationhub.sync;

import integrationhub.domain.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Até onde cada canal já foi lido, e o resultado da última leitura (para o painel). */
@Entity
@Table(name = "sync_cursors")
public class SyncCursor {

    @Id
    @Column(length = 20)
    private String channel;

    @Column(name = "orders_since", nullable = false)
    private Instant ordersSince;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected SyncCursor() {
    }

    SyncCursor(Channel channel, Instant ordersSince) {
        this.channel = channel.name();
        this.ordersSince = ordersSince;
    }

    void success(Instant newSince, Instant now) {
        ordersSince = newSince;
        lastRunAt = now;
        lastError = null;
    }

    void failure(String error, Instant now) {
        lastRunAt = now;
        lastError = error;
    }

    public Channel getChannel() { return Channel.valueOf(channel); }
    public Instant getOrdersSince() { return ordersSince; }
    public Instant getLastRunAt() { return lastRunAt; }
    public String getLastError() { return lastError; }
}
