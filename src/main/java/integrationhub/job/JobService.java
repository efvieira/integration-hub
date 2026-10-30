package integrationhub.job;

import integrationhub.connector.MarketplaceConnector;
import integrationhub.domain.Channel;
import integrationhub.domain.ExternalOrder;
import integrationhub.erp.ErpGateway;
import integrationhub.messaging.JobQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Cria, executa e reprocessa jobs de integração. */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobs;
    private final JobQueue queue;
    private final ErpGateway erp;
    private final Map<Channel, MarketplaceConnector> connectors;
    private final JsonMapper json;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration baseBackoff;

    public JobService(JobRepository jobs, JobQueue queue, ErpGateway erp, Map<Channel, MarketplaceConnector> connectors,
                      JsonMapper json, Clock clock,
                      @Value("${hub.retry.max-attempts:5}") int maxAttempts,
                      @Value("${hub.retry.base-backoff:PT5S}") Duration baseBackoff) {
        this.jobs = jobs;
        this.queue = queue;
        this.erp = erp;
        this.connectors = connectors;
        this.json = json;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
    }

    /** Registra o pedido uma única vez por canal; pedidos já vistos são ignorados. */
    @Transactional
    public Optional<IntegrationJob> registerOrder(ExternalOrder order) {
        if (jobs.existsByTypeAndChannelAndReference(IntegrationJob.Type.ORDER_TO_ERP, order.channel(), order.externalId())) {
            return Optional.empty();
        }
        var job = jobs.save(new IntegrationJob(IntegrationJob.Type.ORDER_TO_ERP, order.channel(), order.externalId(),
                json.writeValueAsString(order), clock.instant()));
        queue.enqueue(job);
        return Optional.of(job);
    }

    @Transactional
    public IntegrationJob registerStockSync(Channel channel, Map<String, Integer> levels) {
        var job = jobs.save(new IntegrationJob(IntegrationJob.Type.STOCK_TO_MARKETPLACE, channel, "stock-" + UUID.randomUUID(),
                json.writeValueAsString(levels), clock.instant()));
        queue.enqueue(job);
        return job;
    }

    /**
     * Executa o job. Falhas temporárias voltam para a fila com backoff exponencial; erros de
     * negócio ou tentativas esgotadas vão para FAILED e para a DLQ.
     */
    @Transactional
    public void process(UUID jobId) {
        var job = jobs.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != IntegrationJob.Status.PENDING) {
            return;
        }
        try {
            job.succeeded(execute(job), clock.instant());
        } catch (RuntimeException e) {
            var retryable = !(e instanceof ErpGateway.ErpRejectedException);
            if (job.failed(e.getMessage(), maxAttempts, retryable, clock.instant())) {
                var delay = baseBackoff.multipliedBy(1L << (job.getAttempts() - 1));
                log.warn("Job {} falhou (tentativa {}), nova tentativa em {}: {}", jobId, job.getAttempts(), delay, e.getMessage());
                queue.retryLater(job, delay);
            } else {
                log.error("Job {} enviado para a fila de falhas: {}", jobId, e.getMessage());
                queue.deadLetter(job);
            }
        }
    }

    @Transactional
    public IntegrationJob reprocess(UUID jobId) {
        var job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job não encontrado"));
        if (job.getStatus() != IntegrationJob.Status.FAILED) {
            throw new IllegalStateException("Só jobs com falha podem ser reprocessados");
        }
        job.reset(clock.instant());
        queue.enqueue(job);
        return job;
    }

    private String execute(IntegrationJob job) {
        return switch (job.getType()) {
            case ORDER_TO_ERP -> erp.createOrder(json.readValue(job.getPayload(), ExternalOrder.class));
            case STOCK_TO_MARKETPLACE -> {
                Map<String, Integer> levels = json.readValue(job.getPayload(), new TypeReference<>() {
                });
                var connector = connectors.get(job.getChannel());
                levels.forEach(connector::updateStock);
                yield levels.size() + " SKUs atualizados";
            }
        };
    }
}
