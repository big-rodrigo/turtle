package turtle.payment.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import turtle.booking.domain.BookingStatus;
import turtle.payment.domain.CheckoutPreference;
import turtle.payment.domain.PaymentInfo;
import turtle.payment.domain.PaymentStatus;
import turtle.payment.domain.service.PaymentGateway;
import turtle.payment.api.PaymentFlowTestHelper.TestBooking;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
class PaymentFlowTest {

    @InjectMock
    PaymentGateway paymentGateway;

    @Inject
    PaymentFlowTestHelper helper;

    private static final AtomicLong counter = new AtomicLong(System.nanoTime());

    // Fixed user IDs matching @TestSecurity annotations
    static final long COACH_ID = 900001L;
    static final long CLIENT_ID = 900002L;

    @BeforeEach
    void setupMocks() {
        Mockito.when(paymentGateway.createPreference(anyLong(), anyString(), any(BigDecimal.class), anyString(), anyString()))
                .thenReturn(new CheckoutPreference("PREF_TEST", "https://checkout.test/pay"));
    }

    // ===== Core guard: coach cannot confirm before payment =====

    @Test
    @TestSecurity(user = "900001", roles = "COACH")
    void coachConfirmOnPendingPaymentReturns409() {
        TestBooking tb = helper.createPendingBooking(new BigDecimal("100.00"), CLIENT_ID, COACH_ID);

        given()
                .contentType(ContentType.JSON)
                .when().patch("/bookings/" + tb.bookingId() + "/confirm")
                .then()
                .statusCode(409);
    }

    // ===== Paid session: preference returns checkoutUrl =====

    @Test
    @TestSecurity(user = "900002", roles = "CLIENT")
    void paidSession_createPreferenceReturnsCheckoutUrl() {
        TestBooking tb = helper.createPendingBooking(new BigDecimal("100.00"), CLIENT_ID, COACH_ID);

        given()
                .contentType(ContentType.JSON)
                .when().post("/bookings/" + tb.bookingId() + "/payment/preference")
                .then()
                .statusCode(200)
                .body("preferenceId", equalTo("PREF_TEST"))
                .body("checkoutUrl", equalTo("https://checkout.test/pay"))
                .body("free", equalTo(false));
    }

    // ===== Free session: skips payment, moves to AWAITING_COACH =====

    @Test
    @TestSecurity(user = "900002", roles = "CLIENT")
    void freeSession_skipsPaymentAndMovesToAwaitingCoach() {
        TestBooking tb = helper.createPendingBooking(null, CLIENT_ID, COACH_ID);

        given()
                .contentType(ContentType.JSON)
                .when().post("/bookings/" + tb.bookingId() + "/payment/preference")
                .then()
                .statusCode(200)
                .body("free", equalTo(true))
                .body("preferenceId", nullValue())
                .body("checkoutUrl", nullValue());

        given()
                .when().get("/bookings/" + tb.bookingId())
                .then()
                .statusCode(200)
                .body("status", equalTo("AWAITING_COACH"));
    }

    // ===== Webhook: approved payment → AWAITING_COACH =====

    @Test
    void webhookApproval_movesBookingToAwaitingCoach() {
        TestBooking tb = helper.createPendingBooking(new BigDecimal("100.00"), CLIENT_ID, COACH_ID);
        helper.createPaymentRecord(tb.bookingId());

        String extPayId = "EXT_APPROVED_" + counter.incrementAndGet();
        Mockito.when(paymentGateway.getPayment(extPayId))
                .thenReturn(new PaymentInfo(extPayId, "approved", new BigDecimal("200.00"), tb.bookingId().toString()));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"type":"payment","action":"payment.created","data":{"id":"%s"}}
                        """.formatted(extPayId))
                .when().post("/payments/webhook")
                .then()
                .statusCode(200);

        helper.assertBookingStatus(tb.bookingId(), BookingStatus.AWAITING_COACH);
        helper.assertPaymentStatus(tb.bookingId(), PaymentStatus.APPROVED);
    }

    // ===== Webhook: rejected payment keeps PENDING_PAYMENT =====

    @Test
    void webhookRejection_keepsBookingPendingPayment() {
        TestBooking tb = helper.createPendingBooking(new BigDecimal("100.00"), CLIENT_ID, COACH_ID);
        helper.createPaymentRecord(tb.bookingId());

        String extPayId = "EXT_REJECTED_" + counter.incrementAndGet();
        Mockito.when(paymentGateway.getPayment(extPayId))
                .thenReturn(new PaymentInfo(extPayId, "rejected", new BigDecimal("200.00"), tb.bookingId().toString()));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"type":"payment","action":"payment.created","data":{"id":"%s"}}
                        """.formatted(extPayId))
                .when().post("/payments/webhook")
                .then()
                .statusCode(200);

        helper.assertBookingStatus(tb.bookingId(), BookingStatus.PENDING_PAYMENT);
        helper.assertPaymentStatus(tb.bookingId(), PaymentStatus.REJECTED);
    }

    // ===== Full paid flow: coach confirms after payment =====

    @Test
    @TestSecurity(user = "900001", roles = "COACH")
    void fullPaidFlow_coachConfirmsAfterPayment() {
        TestBooking tb = helper.createPaidBooking(CLIENT_ID, COACH_ID);

        given()
                .contentType(ContentType.JSON)
                .when().patch("/bookings/" + tb.bookingId() + "/confirm")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    // ===== Coach reject triggers refund =====

    @Test
    @TestSecurity(user = "900001", roles = "COACH")
    void coachReject_triggersRefund() {
        TestBooking tb = helper.createPaidBooking(CLIENT_ID, COACH_ID);
        String extPayId = helper.getPaymentExternalId(tb.bookingId());

        given()
                .contentType(ContentType.JSON)
                .when().patch("/bookings/" + tb.bookingId() + "/reject")
                .then()
                .statusCode(200)
                .body("status", equalTo("REJECTED"));

        Mockito.verify(paymentGateway).refund(extPayId);
    }

    // ===== Client cancel from PENDING_PAYMENT: no refund =====

    @Test
    @TestSecurity(user = "900002", roles = "CLIENT")
    void clientCancelFromPendingPayment_noRefund() {
        TestBooking tb = helper.createPendingBooking(new BigDecimal("100.00"), CLIENT_ID, COACH_ID);

        given()
                .when().delete("/bookings/" + tb.bookingId())
                .then()
                .statusCode(204);

        Mockito.verify(paymentGateway, Mockito.never()).refund(anyString());
    }

    // ===== Client cancel from AWAITING_COACH: triggers refund =====

    @Test
    @TestSecurity(user = "900002", roles = "CLIENT")
    void clientCancelFromAwaitingCoach_triggersRefund() {
        TestBooking tb = helper.createPaidBooking(CLIENT_ID, COACH_ID);
        String extPayId = helper.getPaymentExternalId(tb.bookingId());

        given()
                .when().delete("/bookings/" + tb.bookingId())
                .then()
                .statusCode(204);

        Mockito.verify(paymentGateway).refund(extPayId);
    }

    // ===== Client cannot use coach-only endpoints =====

    @Test
    @TestSecurity(user = "900002", roles = "CLIENT")
    void clientCannotConfirmBooking() {
        given()
                .contentType(ContentType.JSON)
                .when().patch("/bookings/1/confirm")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "900002", roles = "CLIENT")
    void clientCannotRejectBooking() {
        given()
                .contentType(ContentType.JSON)
                .when().patch("/bookings/1/reject")
                .then()
                .statusCode(403);
    }

    // ===== Preference creation requires PENDING_PAYMENT =====

    @Test
    @TestSecurity(user = "900002", roles = "CLIENT")
    void createPreferenceOnNonPendingBookingReturns409() {
        TestBooking tb = helper.createPaidBooking(CLIENT_ID, COACH_ID);

        given()
                .contentType(ContentType.JSON)
                .when().post("/bookings/" + tb.bookingId() + "/payment/preference")
                .then()
                .statusCode(409);
    }

    // ===== Webhook with non-payment type is ignored =====

    @Test
    void webhookWithNonPaymentType_isIgnored() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"type":"merchant_order","action":"created","data":{"id":"12345"}}
                        """)
                .when().post("/payments/webhook")
                .then()
                .statusCode(200);

        Mockito.verify(paymentGateway, Mockito.never()).getPayment(anyString());
    }
}
