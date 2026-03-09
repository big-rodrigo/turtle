package turtle.payment.api.dto;

public record PaymentPreferenceResponse(
        String preferenceId,
        String checkoutUrl,
        boolean free
) {}
