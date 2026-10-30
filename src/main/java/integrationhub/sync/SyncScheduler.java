package integrationhub.sync;

import integrationhub.connector.MarketplaceConnector;
import integrationhub.domain.Channel;
import integrationhub.erp.ErpGateway;
import integrationhub.job.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Pedidos: lê cada marketplace a partir do último cursor (com uma margem para não perder
 * pedidos criados durante a leitura anterior) e cria um job por pedido novo.
 * Estoque: lê o saldo do ERP e cria um job de publicação por canal.
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);
    private static final Duration OVERLAP = Duration.ofMinutes(2);

    private final Map<Channel, MarketplaceConnector> connectors;
    private final CursorRepository cursors;
    private final JobService jobs;
    private final ErpGateway erp;
    private final Clock clock;

    public SyncScheduler(Map<Channel, MarketplaceConnector> connectors, CursorRepository cursors, JobService jobs,
                         ErpGateway erp, Clock clock) {
        this.connectors = connectors;
        this.cursors = cursors;
        this.jobs = jobs;
        this.erp = erp;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hub.orders-poll-interval:PT30S}", initialDelayString = "${hub.initial-delay:PT10S}")
    @Transactional
    public int pollOrders() {
        int created = 0;
        for (var connector : connectors.values()) {
            var cursor = cursors.findById(connector.channel().name())
                    .orElseGet(() -> cursors.save(new SyncCursor(connector.channel(), clock.instant().minus(Duration.ofDays(1)))));
            var startedAt = clock.instant();
            try {
                for (var order : connector.fetchOrders(cursor.getOrdersSince())) {
                    if (jobs.registerOrder(order).isPresent()) {
                        created++;
                    }
                }
                cursor.success(startedAt.minus(OVERLAP), startedAt);
            } catch (RuntimeException e) {
                // um canal fora do ar não impede a leitura dos outros
                cursor.failure(e.getMessage(), startedAt);
                log.warn("Falha ao ler pedidos de {}: {}", connector.channel(), e.getMessage());
            }
        }
        return created;
    }

    @Scheduled(fixedDelayString = "${hub.stock-sync-interval:PT5M}", initialDelayString = "${hub.initial-delay:PT10S}")
    public int syncStock() {
        Map<String, Integer> levels;
        try {
            levels = erp.stockLevels();
        } catch (RuntimeException e) {
            log.warn("Sincronização de estoque adiada: {}", e.getMessage());
            return 0;
        }
        connectors.keySet().forEach(channel -> jobs.registerStockSync(channel, levels));
        return connectors.size();
    }
}
