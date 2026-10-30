package integrationhub.connector;

import integrationhub.domain.Channel;
import integrationhub.domain.ExternalOrder;

import java.time.Instant;
import java.util.List;

/**
 * Porta de cada marketplace. O resto do hub só conhece esta interface: adicionar um canal
 * (Amazon, Magalu) é escrever um adaptador novo.
 */
public interface MarketplaceConnector {

    Channel channel();

    /** Pedidos criados a partir de {@code since}. Pode devolver pedidos já vistos; o hub deduplica. */
    List<ExternalOrder> fetchOrders(Instant since);

    void updateStock(String sku, int quantity);

    /** Erro que vale a pena tentar de novo (timeout, 429, 5xx). */
    class TransientException extends RuntimeException {
        public TransientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
