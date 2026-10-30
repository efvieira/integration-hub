package integrationhub.job;

import integrationhub.domain.Channel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<IntegrationJob, UUID> {

    boolean existsByTypeAndChannelAndReference(IntegrationJob.Type type, Channel channel, String reference);

    List<IntegrationJob> findByStatusOrderByUpdatedAtDesc(IntegrationJob.Status status, Pageable page);

    List<IntegrationJob> findAllByOrderByUpdatedAtDesc(Pageable page);

    long countByChannelAndStatus(Channel channel, IntegrationJob.Status status);
}
