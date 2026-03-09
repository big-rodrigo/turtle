package turtle.booking.api;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.booking.api.dto.BookingResourceRequest;
import turtle.booking.api.dto.BookingResourceResponse;
import turtle.booking.api.dto.BookingResponse;
import turtle.booking.api.dto.CreateBookingRequest;
import turtle.booking.application.BookingApplicationService;
import turtle.booking.domain.Booking;
import turtle.booking.domain.BookingMaterial;
import turtle.coaching.api.dto.CoachingServiceResponse.ExtraServiceSummary;
import turtle.identity.domain.UserRole;
import turtle.payment.application.PaymentApplicationService;
import turtle.payment.domain.Payment;
import turtle.payment.domain.PaymentStatus;

import java.util.List;

@Tag(name = "Bookings", description = "Create and manage coaching session bookings")
@SecurityRequirement(name = "bearerAuth")
@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class BookingResource {

    @Inject
    BookingApplicationService bookingService;

    @Inject
    PaymentApplicationService paymentService;

    @Inject
    SecurityIdentity identity;

    @Operation(summary = "Create a booking (CLIENT)", description = "Book one or more consecutive availability slots with a coach. All slots must belong to the same coach and be adjacent.")
    @APIResponse(responseCode = "201", description = "Booking created with PENDING_PAYMENT status",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error, slots not consecutive, or slots belong to different coaches")
    @APIResponse(responseCode = "403", description = "Only CLIENTs can create bookings")
    @APIResponse(responseCode = "409", description = "One or more slots are already booked")
    @POST
    @RolesAllowed("CLIENT")
    public Response create(@Valid CreateBookingRequest req) {
        Long clientId = Long.parseLong(identity.getPrincipal().getName());
        Booking booking = bookingService.create(clientId, req.availabilityIds(), req.notes(), req.extraServiceIds());
        return Response.status(201).entity(toResponse(booking)).build();
    }

    @Operation(summary = "List bookings", description = "Returns all bookings for the authenticated user. COACHes see bookings where they are the coach; CLIENTs see their own bookings.")
    @APIResponse(responseCode = "200", description = "List of bookings",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @GET
    public List<BookingResponse> list() {
        Long userId = Long.parseLong(identity.getPrincipal().getName());
        UserRole role = identity.hasRole("COACH") ? UserRole.COACH : UserRole.CLIENT;
        return bookingService.listForUser(userId, role).stream()
                .map(this::toResponse)
                .toList();
    }

    @Operation(summary = "Get a booking by ID")
    @APIResponse(responseCode = "200", description = "Booking found",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "403", description = "Booking does not belong to the caller")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @GET
    @Path("/{id}")
    public BookingResponse get(@PathParam("id") Long id) {
        Long userId = Long.parseLong(identity.getPrincipal().getName());
        return toResponse(bookingService.getById(id, userId));
    }

    @Operation(summary = "Confirm a booking (COACH)", description = "COACHes use this to confirm presence for a paid booking.")
    @APIResponse(responseCode = "200", description = "Booking confirmed",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "403", description = "Only the booked COACH can confirm")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @APIResponse(responseCode = "409", description = "Booking is not awaiting coach confirmation")
    @PATCH
    @Path("/{id}/confirm")
    @RolesAllowed("COACH")
    public BookingResponse confirm(@PathParam("id") Long id) {
        Long coachId = Long.parseLong(identity.getPrincipal().getName());
        return toResponse(bookingService.confirm(id, coachId));
    }

    @Operation(summary = "Reject a booking (COACH)", description = "COACHes use this to decline a paid booking. Triggers an automatic refund.")
    @APIResponse(responseCode = "200", description = "Booking rejected",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "403", description = "Only the booked COACH can reject")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @PATCH
    @Path("/{id}/reject")
    @RolesAllowed("COACH")
    public BookingResponse reject(@PathParam("id") Long id) {
        Long coachId = Long.parseLong(identity.getPrincipal().getName());
        return toResponse(bookingService.reject(id, coachId));
    }

    @Operation(summary = "Cancel a booking (CLIENT)", description = "CLIENTs use this to cancel their own booking. All reserved slots are freed.")
    @APIResponse(responseCode = "204", description = "Booking cancelled")
    @APIResponse(responseCode = "403", description = "Only the CLIENT who created the booking can cancel it")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @DELETE
    @Path("/{id}")
    public Response cancel(@PathParam("id") Long id) {
        Long clientId = Long.parseLong(identity.getPrincipal().getName());
        bookingService.cancel(id, clientId);
        return Response.noContent().build();
    }

    @Operation(summary = "Add a resource to a booking (COACH)", description = "Coaches can attach links and materials (e.g. Google Drive, YouTube) to confirmed or awaiting-coach bookings.")
    @APIResponse(responseCode = "201", description = "Resource added",
            content = @Content(schema = @Schema(implementation = BookingResourceResponse.class)))
    @APIResponse(responseCode = "403", description = "Only the booked COACH can add resources")
    @APIResponse(responseCode = "409", description = "Booking is not in a valid status for adding resources")
    @POST
    @Path("/{id}/resources")
    @RolesAllowed("COACH")
    public Response addResource(@PathParam("id") Long id, @Valid BookingResourceRequest req) {
        Long coachId = Long.parseLong(identity.getPrincipal().getName());
        BookingMaterial resource = bookingService.addResource(id, coachId, req.title(), req.url(), req.description());
        return Response.status(201).entity(toResourceResponse(resource)).build();
    }

    @Operation(summary = "List resources for a booking", description = "Returns all resources/materials attached to a booking. Available to both coach and client.")
    @APIResponse(responseCode = "200", description = "List of resources",
            content = @Content(schema = @Schema(implementation = BookingResourceResponse.class)))
    @GET
    @Path("/{id}/resources")
    public List<BookingResourceResponse> listResources(@PathParam("id") Long id) {
        Long userId = Long.parseLong(identity.getPrincipal().getName());
        return bookingService.listResources(id, userId).stream()
                .map(this::toResourceResponse)
                .toList();
    }

    @Operation(summary = "Remove a resource from a booking (COACH)")
    @APIResponse(responseCode = "204", description = "Resource removed")
    @APIResponse(responseCode = "403", description = "Only the booked COACH can remove resources")
    @APIResponse(responseCode = "404", description = "Resource not found")
    @DELETE
    @Path("/{id}/resources/{resourceId}")
    @RolesAllowed("COACH")
    public Response removeResource(@PathParam("id") Long id, @PathParam("resourceId") Long resourceId) {
        Long coachId = Long.parseLong(identity.getPrincipal().getName());
        bookingService.removeResource(id, coachId, resourceId);
        return Response.noContent().build();
    }

    private BookingResourceResponse toResourceResponse(BookingMaterial r) {
        return new BookingResourceResponse(r.id, r.title, r.url, r.description, r.createdAt);
    }

    private BookingResponse toResponse(Booking b) {
        List<Long> ids = b.slots.stream().map(s -> s.id).toList();
        List<ExtraServiceSummary> extras = b.extras.stream()
                .map(e -> new ExtraServiceSummary(e.id, e.name, e.description))
                .toList();
        Long serviceId = b.service != null ? b.service.id : null;
        String serviceName = b.service != null ? b.service.name : null;
        Payment payment = paymentService.getPaymentForBooking(b.id);
        PaymentStatus paymentStatus = payment != null ? payment.status : null;
        List<BookingResourceResponse> resources = b.resources.stream()
                .map(this::toResourceResponse)
                .toList();
        return new BookingResponse(
                b.id, b.client.id, b.client.name,
                b.coach.id, b.coach.name,
                serviceId, serviceName,
                ids, b.startsAt(), b.endsAt(),
                b.status, paymentStatus, b.notes, b.createdAt, extras, resources);
    }
}
