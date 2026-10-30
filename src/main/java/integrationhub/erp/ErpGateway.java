package integrationhub.erp;

import integrationhub.domain.ExternalOrder;

import java.util.Map;

/** Porta do ERP (Bling, Tiny, Omie, SAP B1...). */
public interface ErpGateway {

    /**
     * Cria o pedido no ERP e devolve o número gerado. Deve ser idempotente pela referência
     * externa: o hub pode reenviar o mesmo pedido depois de uma falha de rede.
     */
    String createOrder(ExternalOrder order);

    /** Saldo atual por SKU, fonte da verdade do estoque. */
    Map<String, Integer> stockLevels();

    class ErpUnavailableException extends RuntimeException {
        public ErpUnavailableException(String message) {
            super(message);
        }
    }

    /** Erro de negócio: tentar de novo não resolve (ex.: SKU inexistente no ERP). */
    class ErpRejectedException extends RuntimeException {
        public ErpRejectedException(String message) {
            super(message);
        }
    }
}
