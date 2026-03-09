package turtle.payment.api;

import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.payment.api.dto.MercadoPagoWebhookPayload;
import turtle.payment.api.dto.PaymentPreferenceResponse;
import turtle.payment.application.PaymentApplicationService;
import turtle.payment.domain.CheckoutPreference;

@Tag(name = "Payments", description = "Payment processing via MercadoPago Checkout Pro")
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentApplicationService paymentService;

    @Inject
    SecurityIdentity identity;

    @Operation(summary = "Create payment preference (CLIENT)",
            description = "Creates a MercadoPago Checkout Pro preference for the booking. "
                    + "Returns checkoutUrl to redirect the client to MercadoPago. "
                    + "If the session is free (no price set), checkoutUrl will be null and the booking moves directly to AWAITING_COACH.")
    @APIResponse(responseCode = "200", description = "Preference created or free session confirmed")
    @APIResponse(responseCode = "403", description = "Booking does not belong to the caller")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @APIResponse(responseCode = "409", description = "Booking is not in PENDING_PAYMENT status")
    @SecurityRequirement(name = "bearerAuth")
    @POST
    @Path("/bookings/{bookingId}/payment/preference")
    @RolesAllowed("CLIENT")
    public PaymentPreferenceResponse createPreference(@PathParam("bookingId") Long bookingId) {
        Long clientId = Long.parseLong(identity.getPrincipal().getName());
        CheckoutPreference preference = paymentService.createPreference(bookingId, clientId);
        if (preference == null) {
            return new PaymentPreferenceResponse(null, null, true);
        }
        return new PaymentPreferenceResponse(preference.preferenceId(), preference.checkoutUrl(), false);
    }

    @Operation(summary = "MercadoPago webhook (internal)",
            description = "Receives payment status notifications from MercadoPago. "
                    + "Always returns 200 to prevent MP from retrying on business logic errors.")
    @APIResponse(responseCode = "200", description = "Notification received")
    @POST
    @Path("/payments/webhook")
    @PermitAll
    public Response handleWebhook(MercadoPagoWebhookPayload payload) {
        try {
            if (payload != null && payload.data() != null && payload.data().id() != null) {
                paymentService.processWebhook(payload.type(), payload.data().id());
            }
        } catch (Exception e) {
            Log.warnf("Webhook processing error (non-fatal): %s", e.getMessage());
        }
        return Response.ok().build();
    }
}
