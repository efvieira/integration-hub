package integrationhub.connector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import integrationhub.domain.Channel;
import integrationhub.domain.ExternalOrder;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Adaptador para a API pública do Mercado Livre (/orders/search e /items/{id}).
 * Ativado quando há access token configurado; sem ele o hub usa o sandbox.
 */
public class MercadoLivreConnector implements MarketplaceConnector {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<MlOrder> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MlOrder(long id, String date_created, Buyer buyer, List<OrderItem> order_items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Buyer(String nickname) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderItem(Item item, int quantity, BigDecimal unit_price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, String seller_sku) {
    }

    private final RestClient client;
    private final String sellerId;

    public MercadoLivreConnector(RestClient.Builder builder, String baseUrl, String accessToken, String sellerId) {
        this.client = builder.baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new TransientException("Mercado Livre respondeu " + res.getStatusCode(), null);
                })
                .defaultStatusHandler(status -> status.value() == 429, (req, res) -> {
                    throw new TransientException("Limite de requisições do Mercado Livre", null);
                })
                .build();
        this.sellerId = sellerId;
    }

    @Override
    public Channel channel() {
        return Channel.MERCADO_LIVRE;
    }

    @Override
    public List<ExternalOrder> fetchOrders(Instant since) {
        try {
            var response = client.get()
                    .uri(u -> u.path("/orders/search").queryParam("seller", sellerId)
                            .queryParam("order.date_created.from", since.toString()).build())
                    .retrieve()
                    .body(SearchResponse.class);
            return response == null ? List.of() : response.results().stream().map(this::toOrder).toList();
        } catch (ResourceAccessException e) {
            throw new TransientException("Mercado Livre indisponível", e);
        }
    }

    @Override
    public void updateStock(String sku, int quantity) {
        // No ML o estoque é por anúncio; o SKU do vendedor é usado como id do anúncio aqui por simplicidade.
        client.put().uri("/items/{id}", sku).body(Map.of("available_quantity", quantity)).retrieve().toBodilessEntity();
    }

    private ExternalOrder toOrder(MlOrder order) {
        var lines = order.order_items().stream()
                .map(i -> new ExternalOrder.Line(i.item().seller_sku() != null ? i.item().seller_sku() : i.item().id(),
                        i.quantity(), i.unit_price()))
                .toList();
        return new ExternalOrder(Channel.MERCADO_LIVRE, String.valueOf(order.id()),
                order.buyer() == null ? null : order.buyer().nickname(), lines,
                OffsetDateTime.parse(order.date_created()).toInstant());
    }
}
