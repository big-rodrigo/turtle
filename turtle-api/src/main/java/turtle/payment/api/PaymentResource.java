package turtle.payment.api;

import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.payment.api.dto.MercadoPagoWebhookPayload;
import turtle.payment.application.PaymentApplicationService;

@Tag(name = "Payments", description = "Payment processing via MercadoPago Checkout Pro")
@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentApplicationService paymentService;

    @Operation(summary = "MercadoPago webhook (internal)",
            description = "Receives payment status notifications from MercadoPago. "
                    + "Always returns 200 to prevent MP from retrying on business logic errors.")
    @APIResponse(responseCode = "200", description = "Notification received")
    @POST
    @Path("/webhook")
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
