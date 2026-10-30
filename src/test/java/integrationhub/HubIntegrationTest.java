package integrationhub;

import integrationhub.connector.MarketplaceConnector;
import integrationhub.connector.SandboxMarketplace;
import integrationhub.domain.Channel;
import integrationhub.erp.SimulatedErp;
import integrationhub.job.IntegrationJob;
import integrationhub.job.JobRepository;
import integrationhub.job.JobService;
import integrationhub.job.JobWorker;
import integrationhub.messaging.RabbitTopology;
import integrationhub.sync.SyncScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HubIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SyncScheduler sync;
    @Autowired JobService jobService;
    @Autowired JobWorker worker;
    @Autowired JobRepository jobs;
    @Autowired SimulatedErp erp;
    @Autowired Map<Channel, MarketplaceConnector> connectors;

    @MockitoBean RabbitTemplate rabbit;

    @AfterEach
    void erpBackOnline() {
        erp.setOnline(true);
    }

    private IntegrationJob orderJob(Channel channel, String externalId) {
        return jobs.findAll().stream()
                .filter(j -> j.getChannel() == channel && j.getReference().equals(externalId))
                .findFirst().orElseThrow();
    }

    private void newOrder(String channel, String externalId, String sku) throws Exception {
        mvc.perform(post("/sandbox/{channel}/orders", channel).contentType(MediaType.APPLICATION_JSON).content("""
                        {"externalId": "%s", "buyerName": "COMPRADOR", "lines": [{"sku": "%s", "quantity": 2, "unitPrice": 39.90}]}"""
                        .formatted(externalId, sku)))
                .andExpect(status().isAccepted());
    }

    @Test
    void marketplaceOrderReachesErpOnlyOnce() throws Exception {
        newOrder("MERCADO_LIVRE", "ML-1001", "CAMISETA-M");

        assertThat(sync.pollOrders()).isEqualTo(1);
        assertThat(sync.pollOrders()).isZero(); // leitura com sobreposição não duplica
        var job = orderJob(Channel.MERCADO_LIVRE, "ML-1001");
        verify(rabbit).convertAndSend(RabbitTopology.EXCHANGE, "orders", job.getId().toString());

        worker.onJob(job.getId().toString());
        worker.onJob(job.getId().toString()); // reentrega da fila
        var done = jobs.findById(job.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(IntegrationJob.Status.DONE);
        assertThat(done.getResult()).startsWith("PV-");
    }

    @Test
    void erpOutageRetriesWithBackoffThenFailsAndCanBeReprocessed() throws Exception {
        newOrder("SHOPEE", "SH-2002", "CANECA-300");
        sync.pollOrders();
        var job = orderJob(Channel.SHOPEE, "SH-2002");
        erp.setOnline(false);

        jobService.process(job.getId());
        verify(rabbit).convertAndSend(eq(RabbitTopology.RETRY_EXCHANGE), eq("orders"), eq(job.getId().toString()), any(MessagePostProcessor.class));
        jobService.process(job.getId());
        jobService.process(job.getId());
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(IntegrationJob.Status.FAILED);
        verify(rabbit).convertAndSend("", RabbitTopology.DLQ, job.getId().toString());

        mvc.perform(get("/jobs").param("status", "FAILED")).andExpect(jsonPath("$[?(@.reference == 'SH-2002')].attempts").value(3));
        mvc.perform(get("/status")).andExpect(jsonPath("$[?(@.channel == 'SHOPEE')].jobs.FAILED").value(1));

        erp.setOnline(true);
        mvc.perform(post("/jobs/retry-failed")).andExpect(jsonPath("$.reprocessed").value(1));
        jobService.process(job.getId());
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(IntegrationJob.Status.DONE);
        mvc.perform(post("/jobs/{id}/retry", job.getId())).andExpect(status().isConflict());
        mvc.perform(post("/jobs/{id}/retry", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void unknownSkuFailsImmediatelyWithoutRetry() throws Exception {
        newOrder("MERCADO_LIVRE", "ML-3003", "SKU-INEXISTENTE");
        sync.pollOrders();
        var job = orderJob(Channel.MERCADO_LIVRE, "ML-3003");

        jobService.process(job.getId());
        var failed = jobs.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(IntegrationJob.Status.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("não cadastrado");
    }

    @Test
    void erpStockIsPublishedToEveryChannel() throws Exception {
        assertThat(sync.syncStock()).isEqualTo(2);
        jobs.findAll().stream()
                .filter(j -> j.getType() == IntegrationJob.Type.STOCK_TO_MARKETPLACE)
                .forEach(j -> jobService.process(j.getId()));

        var shopee = (SandboxMarketplace) connectors.get(Channel.SHOPEE);
        assertThat(shopee.publishedStock()).containsKey("BONE-AJUSTAVEL");
        mvc.perform(get("/sandbox/MERCADO_LIVRE/stock")).andExpect(jsonPath("$.CAMISETA-P").isNumber());
        verify(rabbit, atLeastOnce()).convertAndSend(eq(RabbitTopology.EXCHANGE), eq("stock"), anyString());

        erp.setOnline(false);
        assertThat(sync.syncStock()).isZero();
        mvc.perform(post("/sandbox/erp").param("online", "true")).andExpect(jsonPath("$.online").value(true));
    }
}
