package integrationhub.api;

import integrationhub.domain.Channel;
import integrationhub.job.IntegrationJob;
import integrationhub.job.JobRepository;
import integrationhub.job.JobService;
import integrationhub.sync.CursorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API do painel de status: saúde de cada canal, jobs recentes e reprocessamento. */
@RestController
@Tag(name = "Painel")
public class StatusController {

    public record ChannelStatus(Channel channel, Instant lastRunAt, String lastError,
                                Map<IntegrationJob.Status, Long> jobs) {
    }

    public record JobView(UUID id, IntegrationJob.Type type, Channel channel, String reference,
                          IntegrationJob.Status status, int attempts, String lastError, String result, Instant updatedAt) {
        static JobView from(IntegrationJob j) {
            return new JobView(j.getId(), j.getType(), j.getChannel(), j.getReference(), j.getStatus(),
                    j.getAttempts(), j.getLastError(), j.getResult(), j.getUpdatedAt());
        }
    }

    private final JobRepository jobs;
    private final JobService service;
    private final CursorRepository cursors;

    public StatusController(JobRepository jobs, JobService service, CursorRepository cursors) {
        this.jobs = jobs;
        this.service = service;
        this.cursors = cursors;
    }

    @GetMapping("/status")
    @Operation(summary = "Resumo por canal: última leitura, erro e jobs por status")
    public List<ChannelStatus> status() {
        return Arrays.stream(Channel.values()).map(channel -> {
            var counts = new EnumMap<IntegrationJob.Status, Long>(IntegrationJob.Status.class);
            for (var s : IntegrationJob.Status.values()) {
                counts.put(s, jobs.countByChannelAndStatus(channel, s));
            }
            var cursor = cursors.findById(channel.name());
            return new ChannelStatus(channel, cursor.map(c -> c.getLastRunAt()).orElse(null),
                    cursor.map(c -> c.getLastError()).orElse(null), counts);
        }).toList();
    }

    @GetMapping("/jobs")
    public List<JobView> list(@RequestParam(required = false) IntegrationJob.Status status,
                              @RequestParam(defaultValue = "50") int limit) {
        var page = PageRequest.of(0, Math.min(limit, 200));
        var result = status == null ? jobs.findAllByOrderByUpdatedAtDesc(page) : jobs.findByStatusOrderByUpdatedAtDesc(status, page);
        return result.stream().map(JobView::from).toList();
    }

    @PostMapping("/jobs/{id}/retry")
    @Operation(summary = "Reprocessa um job que falhou")
    public JobView retry(@PathVariable UUID id) {
        return JobView.from(service.reprocess(id));
    }

    @PostMapping("/jobs/retry-failed")
    @Operation(summary = "Reprocessa todos os jobs com falha (ex.: depois que o ERP voltou)")
    public Map<String, Integer> retryAll() {
        var failed = jobs.findByStatusOrderByUpdatedAtDesc(IntegrationJob.Status.FAILED, PageRequest.of(0, 500));
        failed.forEach(j -> service.reprocess(j.getId()));
        return Map.of("reprocessed", failed.size());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail notFound(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
