package turtle.payment.domain.service;

import turtle.payment.domain.CheckoutPreference;
import turtle.payment.domain.PaymentInfo;

import java.math.BigDecimal;

public interface PaymentGateway {

    CheckoutPreference createPreference(
            Long bookingId,
            String itemTitle,
            BigDecimal amount,
            String payerEmail,
            String notificationUrl
    );

    PaymentInfo getPayment(String externalPaymentId);

    void refund(String externalPaymentId);
}
