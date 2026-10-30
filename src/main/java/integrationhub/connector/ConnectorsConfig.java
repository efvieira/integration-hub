package integrationhub.connector;

import integrationhub.domain.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.EnumMap;
import java.util.Map;

/**
 * Escolhe o adaptador de cada canal: real quando há credenciais, sandbox caso contrário.
 * Assim a demo roda sem contas nos marketplaces e a produção só precisa de variáveis de ambiente.
 */
@Configuration
public class ConnectorsConfig {

    @ConfigurationProperties("connectors")
    public record ConnectorsProperties(MercadoLivre mercadoLivre) {
        public record MercadoLivre(String baseUrl, String accessToken, String sellerId) {
        }
    }

    @Bean
    Map<Channel, MarketplaceConnector> connectors(ConnectorsProperties props, RestClient.Builder builder) {
        var connectors = new EnumMap<Channel, MarketplaceConnector>(Channel.class);
        var ml = props.mercadoLivre();
        connectors.put(Channel.MERCADO_LIVRE, ml != null && ml.accessToken() != null && !ml.accessToken().isBlank()
                ? new MercadoLivreConnector(builder, ml.baseUrl(), ml.accessToken(), ml.sellerId())
                : new SandboxMarketplace(Channel.MERCADO_LIVRE));
        connectors.put(Channel.SHOPEE, new SandboxMarketplace(Channel.SHOPEE));
        return connectors;
    }
}
