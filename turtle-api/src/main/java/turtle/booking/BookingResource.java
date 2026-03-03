package turtle.booking;

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
import turtle.booking.dto.BookingResponse;
import turtle.booking.dto.CreateBookingRequest;
import turtle.user.UserRole;

import java.util.List;

@Tag(name = "Bookings", description = "Create and manage coaching session bookings")
@SecurityRequirement(name = "bearerAuth")
@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class BookingResource {

    @Inject
    BookingService bookingService;

    @Inject
    SecurityIdentity identity;

    @Operation(summary = "Create a booking (CLIENT)", description = "Book one or more consecutive availability slots with a coach. All slots must belong to the same coach and be adjacent.")
    @APIResponse(responseCode = "201", description = "Booking created with PENDING status",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error, slots not consecutive, or slots belong to different coaches")
    @APIResponse(responseCode = "403", description = "Only CLIENTs can create bookings")
    @APIResponse(responseCode = "409", description = "One or more slots are already booked")
    @POST
    @RolesAllowed("CLIENT")
    public Response create(@Valid CreateBookingRequest req) {
        Long clientId = Long.parseLong(identity.getPrincipal().getName());
        Booking booking = bookingService.create(clientId, req.availabilityIds(), req.notes());
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

    @Operation(summary = "Approve a booking (COACH)", description = "COACHes use this to confirm a pending booking on their schedule.")
    @APIResponse(responseCode = "200", description = "Booking approved",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "403", description = "Only the booked COACH can approve")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @PATCH
    @Path("/{id}/approve")
    @RolesAllowed("COACH")
    public BookingResponse approve(@PathParam("id") Long id) {
        Long coachId = Long.parseLong(identity.getPrincipal().getName());
        return toResponse(bookingService.approve(id, coachId));
    }

    @Operation(summary = "Reject a booking (COACH)", description = "COACHes use this to decline a pending booking.")
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

    private BookingResponse toResponse(Booking b) {
        List<Long> ids = b.slots.stream().map(s -> s.id).toList();
        return new BookingResponse(
                b.id, b.client.id, b.client.name,
                b.coach.id, b.coach.name,
                ids, b.startsAt(), b.endsAt(),
                b.status, b.notes, b.createdAt);
    }
}
