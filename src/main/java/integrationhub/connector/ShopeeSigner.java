package integrationhub.connector;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Assinatura das chamadas da Shopee Open Platform v2 (APIs de loja):
 * sign = HMAC-SHA256(partner_key, partner_id + path + timestamp + access_token + shop_id), em hex.
 */
public final class ShopeeSigner {

    private ShopeeSigner() {
    }

    public static String sign(String partnerKey, long partnerId, String path, long timestamp, String accessToken, long shopId) {
        var base = "" + partnerId + path + timestamp + accessToken + shopId;
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(partnerKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
