package integrationhub.messaging;

import integrationhub.job.IntegrationJob;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Component
public class JobQueue {

    private final RabbitTemplate rabbit;

    public JobQueue(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /**
     * Enfileira só depois do commit: se publicasse antes, o worker poderia buscar um job que
     * ainda não existe no banco, ou processar um job cuja transação sofreu rollback.
     */
    public void enqueue(IntegrationJob job) {
        Runnable send = () -> rabbit.convertAndSend(RabbitTopology.EXCHANGE, job.getType().routingKey(), job.getId().toString());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    public void retryLater(IntegrationJob job, Duration delay) {
        rabbit.convertAndSend(RabbitTopology.RETRY_EXCHANGE, job.getType().routingKey(), job.getId().toString(), message -> {
            message.getMessageProperties().setExpiration(String.valueOf(delay.toMillis()));
            return message;
        });
    }

    public void deadLetter(IntegrationJob job) {
        rabbit.convertAndSend("", RabbitTopology.DLQ, job.getId().toString());
    }
}
