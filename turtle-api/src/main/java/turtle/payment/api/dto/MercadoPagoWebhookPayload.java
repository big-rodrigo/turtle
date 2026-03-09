package turtle.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoWebhookPayload(
        String type,
        String action,
        Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String id) {}
}
