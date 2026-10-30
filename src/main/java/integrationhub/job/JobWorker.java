package integrationhub.job;

import integrationhub.messaging.RabbitTopology;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Consumidores das filas. A mensagem carrega só o id; o estado fica no banco. */
@Component
public class JobWorker {

    private final JobService jobs;

    public JobWorker(JobService jobs) {
        this.jobs = jobs;
    }

    @RabbitListener(queues = {RabbitTopology.ORDERS_QUEUE, RabbitTopology.STOCK_QUEUE}, concurrency = "2-4")
    public void onJob(String jobId) {
        jobs.process(UUID.fromString(jobId));
    }
}
