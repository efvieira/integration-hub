package integrationhub.connector;

import integrationhub.domain.Channel;
import integrationhub.domain.ExternalOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Marketplace em memória para demo e testes: recebe pedidos injetados e guarda o estoque publicado. */
public class SandboxMarketplace implements MarketplaceConnector {

    private final Channel channel;
    private final List<ExternalOrder> orders = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();

    public SandboxMarketplace(Channel channel) {
        this.channel = channel;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public List<ExternalOrder> fetchOrders(Instant since) {
        return orders.stream().filter(o -> !o.createdAt().isBefore(since)).toList();
    }

    @Override
    public void updateStock(String sku, int quantity) {
        stock.put(sku, quantity);
    }

    public void receive(ExternalOrder order) {
        orders.add(order);
    }

    public Map<String, Integer> publishedStock() {
        return Map.copyOf(stock);
    }
}
