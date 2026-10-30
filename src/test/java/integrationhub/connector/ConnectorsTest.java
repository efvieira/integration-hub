package integrationhub.connector;

import integrationhub.domain.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConnectorsTest {

    private static final String ML_ORDERS = """
            {"results": [{
              "id": 2000001234,
              "date_created": "2026-03-10T14:22:05.000-03:00",
              "buyer": {"nickname": "COMPRADOR_TESTE"},
              "order_items": [
                {"item": {"id": "MLB123", "seller_sku": "CAMISETA-P"}, "quantity": 2, "unit_price": 49.9},
                {"item": {"id": "MLB456", "seller_sku": null}, "quantity": 1, "unit_price": 10}
              ],
              "campo_extra": true
            }]}""";

    @Test
    void mercadoLivreOrdersAreMappedToTheHubModel() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://ml.test/orders/search?seller=42")))
                .andExpect(header("Authorization", "Bearer token-x"))
                .andRespond(withSuccess(ML_ORDERS, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://ml.test/items/CAMISETA-P")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://ml.test/orders/search")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        var connector = new MercadoLivreConnector(builder, "https://ml.test", "token-x", "42");
        var orders = connector.fetchOrders(Instant.parse("2026-03-10T00:00:00Z"));

        assertThat(connector.channel()).isEqualTo(Channel.MERCADO_LIVRE);
        assertThat(orders).singleElement().satisfies(o -> {
            assertThat(o.externalId()).isEqualTo("2000001234");
            assertThat(o.buyerName()).isEqualTo("COMPRADOR_TESTE");
            assertThat(o.lines()).extracting(l -> l.sku()).containsExactly("CAMISETA-P", "MLB456");
            assertThat(o.total()).isEqualByComparingTo("109.80");
            assertThat(o.createdAt()).isEqualTo(Instant.parse("2026-03-10T17:22:05Z"));
        });

        connector.updateStock("CAMISETA-P", 7);
        assertThatThrownBy(() -> connector.fetchOrders(Instant.EPOCH)).isInstanceOf(MarketplaceConnector.TransientException.class);
        server.verify();
    }

    @Test
    void shopeeSignatureIsDeterministicHexHmac() {
        var first = ShopeeSigner.sign("chave", 1001, "/api/v2/order/get_order_list", 1_700_000_000L, "token", 77);
        var second = ShopeeSigner.sign("chave", 1001, "/api/v2/order/get_order_list", 1_700_000_000L, "token", 77);
        var otherShop = ShopeeSigner.sign("chave", 1001, "/api/v2/order/get_order_list", 1_700_000_000L, "token", 78);

        assertThat(first).hasSize(64).matches("[0-9a-f]+").isEqualTo(second).isNotEqualTo(otherShop);
    }

    @Test
    void sandboxFiltersOrdersBySince() {
        var sandbox = new SandboxMarketplace(Channel.SHOPEE);
        var now = Instant.parse("2026-03-10T12:00:00Z");
        sandbox.receive(new integrationhub.domain.ExternalOrder(Channel.SHOPEE, "1", null, java.util.List.of(), now));
        assertThat(sandbox.fetchOrders(now.plusSeconds(1))).isEmpty();
        assertThat(sandbox.fetchOrders(now)).hasSize(1);
    }
}
