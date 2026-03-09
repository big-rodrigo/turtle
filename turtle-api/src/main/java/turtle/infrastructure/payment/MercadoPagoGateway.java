package turtle.infrastructure.payment;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.preference.Preference;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import turtle.payment.domain.CheckoutPreference;
import turtle.payment.domain.PaymentInfo;
import turtle.payment.domain.service.PaymentGateway;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class MercadoPagoGateway implements PaymentGateway {

    @ConfigProperty(name = "mercadopago.access-token")
    String accessToken;

    @ConfigProperty(name = "mercadopago.back-url.success")
    String backUrlSuccess;

    @ConfigProperty(name = "mercadopago.back-url.failure")
    String backUrlFailure;

    @ConfigProperty(name = "mercadopago.back-url.pending")
    String backUrlPending;

    @Override
    public CheckoutPreference createPreference(
            Long bookingId,
            String itemTitle,
            BigDecimal amount,
            String payerEmail,
            String notificationUrl
    ) {
        try {
            MercadoPagoConfig.setAccessToken(accessToken);

            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title(itemTitle)
                    .quantity(1)
                    .currencyId("BRL")
                    .unitPrice(amount)
                    .build();

            PreferencePayerRequest payer = PreferencePayerRequest.builder()
                    .email(payerEmail)
                    .build();

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(backUrlSuccess)
                    .failure(backUrlFailure)
                    .pending(backUrlPending)
                    .build();

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(List.of(item))
                    .payer(payer)
                    .backUrls(backUrls)
                    .notificationUrl(notificationUrl)
                    .externalReference(bookingId.toString())
                    .build();

            Preference preference = new PreferenceClient().create(request);
            return new CheckoutPreference(preference.getId(), preference.getInitPoint());
        } catch (Exception e) {
            Log.errorf("MercadoPago createPreference failed for booking %d: %s", bookingId, e.getMessage());
            throw new WebApplicationException("Failed to create payment preference: " + e.getMessage(), 502);
        }
    }

    @Override
    public PaymentInfo getPayment(String externalPaymentId) {
        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            com.mercadopago.resources.payment.Payment payment =
                    new PaymentClient().get(Long.parseLong(externalPaymentId));
            return new PaymentInfo(
                    externalPaymentId,
                    payment.getStatus(),
                    payment.getTransactionAmount(),
                    payment.getExternalReference()
            );
        } catch (Exception e) {
            Log.errorf("MercadoPago getPayment failed for id %s: %s", externalPaymentId, e.getMessage());
            throw new WebApplicationException("Failed to fetch payment: " + e.getMessage(), 502);
        }
    }

    @Override
    public void refund(String externalPaymentId) {
        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            new PaymentRefundClient().refund(Long.parseLong(externalPaymentId));
        } catch (Exception e) {
            Log.errorf("MercadoPago refund failed for payment %s: %s", externalPaymentId, e.getMessage());
            throw new WebApplicationException("Failed to process refund: " + e.getMessage(), 502);
        }
    }
}
