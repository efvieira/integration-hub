package integrationhub.erp;

import integrationhub.domain.ExternalOrder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ERP simulado: mantém estoque em memória, baixa o saldo a cada pedido e pode ser "derrubado"
 * pelo sandbox para demonstrar retentativas e a fila de falhas.
 */
@Component
public class SimulatedErp implements ErpGateway {

    private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of(
            "CAMISETA-P", 40, "CAMISETA-M", 55, "CANECA-300", 120, "BONE-AJUSTAVEL", 30));
    private final Map<String, String> ordersByReference = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1000);
    private final AtomicBoolean online = new AtomicBoolean(true);

    @Override
    public synchronized String createOrder(ExternalOrder order) {
        if (!online.get()) {
            throw new ErpUnavailableException("ERP fora do ar (simulado)");
        }
        var reference = order.channel() + ":" + order.externalId();
        var existing = ordersByReference.get(reference);
        if (existing != null) {
            return existing;
        }
        for (var line : order.lines()) {
            if (!stock.containsKey(line.sku())) {
                throw new ErpRejectedException("SKU " + line.sku() + " não cadastrado no ERP");
            }
        }
        order.lines().forEach(l -> stock.merge(l.sku(), -l.quantity(), Integer::sum));
        var number = "PV-" + sequence.incrementAndGet();
        ordersByReference.put(reference, number);
        return number;
    }

    @Override
    public Map<String, Integer> stockLevels() {
        if (!online.get()) {
            throw new ErpUnavailableException("ERP fora do ar (simulado)");
        }
        return Map.copyOf(stock);
    }

    public void setOnline(boolean value) {
        online.set(value);
    }

    public boolean isOnline() {
        return online.get();
    }
}
