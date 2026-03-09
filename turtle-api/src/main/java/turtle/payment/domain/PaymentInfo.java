package turtle.payment.domain;

import java.math.BigDecimal;

public record PaymentInfo(String externalPaymentId, String status, BigDecimal amount, String externalReference) {}
