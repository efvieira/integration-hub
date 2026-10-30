package integrationhub.api;

import integrationhub.connector.MarketplaceConnector;
import integrationhub.connector.SandboxMarketplace;
import integrationhub.domain.Channel;
import integrationhub.domain.ExternalOrder;
import integrationhub.erp.SimulatedErp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/** Simula os sistemas externos para a demo: pedidos chegando nos marketplaces e o ERP caindo. */
@RestController
@RequestMapping("/sandbox")
@Tag(name = "Sandbox")
public class SandboxController {

    public record NewOrder(@NotBlank String externalId, String buyerName, @NotEmpty List<ExternalOrder.Line> lines) {
    }

    private final Map<Channel, MarketplaceConnector> connectors;
    private final SimulatedErp erp;
    private final Clock clock;

    public SandboxController(Map<Channel, MarketplaceConnector> connectors, SimulatedErp erp, Clock clock) {
        this.connectors = connectors;
        this.erp = erp;
        this.clock = clock;
    }

    @PostMapping("/{channel}/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Simula um pedido novo no marketplace (lido na próxima sincronização)")
    public void newOrder(@PathVariable Channel channel, @Valid @RequestBody NewOrder order) {
        sandbox(channel).receive(new ExternalOrder(channel, order.externalId(), order.buyerName(), order.lines(), clock.instant()));
    }

    @GetMapping("/{channel}/stock")
    public Map<String, Integer> stock(@PathVariable Channel channel) {
        return sandbox(channel).publishedStock();
    }

    @PostMapping("/erp")
    @Operation(summary = "Liga/desliga o ERP simulado para ver retentativas e a fila de falhas")
    public Map<String, Boolean> erp(@RequestParam boolean online) {
        erp.setOnline(online);
        return Map.of("online", erp.isOnline());
    }

    private SandboxMarketplace sandbox(Channel channel) {
        if (connectors.get(channel) instanceof SandboxMarketplace sandbox) {
            return sandbox;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, channel + " está conectado à API real");
    }
}
