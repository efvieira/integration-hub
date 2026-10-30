package integrationhub.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Pedido de marketplace já traduzido para o modelo do hub (independente do formato de cada API). */
public record ExternalOrder(Channel channel, String externalId, String buyerName, List<Line> lines, Instant createdAt) {

    public record Line(String sku, int quantity, BigDecimal unitPrice) {
    }

    public BigDecimal total() {
        return lines.stream().map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
