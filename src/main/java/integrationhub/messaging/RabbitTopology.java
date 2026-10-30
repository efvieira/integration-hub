package integrationhub.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de filas:
 * <pre>
 *  hub (exchange) --orders--> hub.orders --worker-- falhou? --> hub.retry (exchange) --> hub.retry.wait
 *                 --stock---> hub.stock                                                  | TTL por mensagem
 *                                                                                         v (dead-letter)
 *                                                                               volta para "hub" com a mesma routing key
 *  esgotou as tentativas --> hub.dlq (inspeção e reprocessamento manual)
 * </pre>
 * O atraso exponencial é feito com a expiração da mensagem na fila de espera, sem plugin.
 */
@Configuration
public class RabbitTopology {

    public static final String EXCHANGE = "hub";
    public static final String RETRY_EXCHANGE = "hub.retry";
    public static final String ORDERS_QUEUE = "hub.orders";
    public static final String STOCK_QUEUE = "hub.stock";
    public static final String WAIT_QUEUE = "hub.retry.wait";
    public static final String DLQ = "hub.dlq";

    @Bean
    Declarables topology() {
        var exchange = new DirectExchange(EXCHANGE);
        var retryExchange = new DirectExchange(RETRY_EXCHANGE);
        Queue orders = QueueBuilder.durable(ORDERS_QUEUE).build();
        Queue stock = QueueBuilder.durable(STOCK_QUEUE).build();
        Queue wait = QueueBuilder.durable(WAIT_QUEUE).deadLetterExchange(EXCHANGE).build();
        Queue dlq = QueueBuilder.durable(DLQ).build();
        Binding ordersBinding = BindingBuilder.bind(orders).to(exchange).with("orders");
        Binding stockBinding = BindingBuilder.bind(stock).to(exchange).with("stock");
        Binding waitOrders = BindingBuilder.bind(wait).to(retryExchange).with("orders");
        Binding waitStock = BindingBuilder.bind(wait).to(retryExchange).with("stock");
        return new Declarables(exchange, retryExchange, orders, stock, wait, dlq,
                ordersBinding, stockBinding, waitOrders, waitStock);
    }
}
