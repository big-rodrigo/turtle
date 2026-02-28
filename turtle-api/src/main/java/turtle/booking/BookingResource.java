package turtle.booking;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
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
    JsonWebToken jwt;

    @Operation(summary = "Create a booking (CLIENT)", description = "Book an available time slot with a coach. Only clients can create bookings.")
    @APIResponse(responseCode = "201", description = "Booking created with PENDING status",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error or slot not available")
    @APIResponse(responseCode = "403", description = "Only CLIENTs can create bookings")
    @POST
    @RolesAllowed("CLIENT")
    public Response create(@Valid CreateBookingRequest req) {
        Long clientId = Long.parseLong(jwt.getSubject());
        Booking booking = bookingService.create(clientId, req.availabilityId(), req.notes());
        return Response.status(201).entity(toResponse(booking)).build();
    }

    @Operation(summary = "List bookings", description = "Returns all bookings for the authenticated user. COACHes see bookings where they are the coach; CLIENTs see their own bookings.")
    @APIResponse(responseCode = "200", description = "List of bookings",
            content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @GET
    public List<BookingResponse> list() {
        Long userId = Long.parseLong(jwt.getSubject());
        UserRole role = jwt.getGroups().contains("COACH") ? UserRole.COACH : UserRole.CLIENT;
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
        Long userId = Long.parseLong(jwt.getSubject());
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
        Long coachId = Long.parseLong(jwt.getSubject());
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
        Long coachId = Long.parseLong(jwt.getSubject());
        return toResponse(bookingService.reject(id, coachId));
    }

    @Operation(summary = "Cancel a booking (CLIENT)", description = "CLIENTs use this to cancel their own booking.")
    @APIResponse(responseCode = "204", description = "Booking cancelled")
    @APIResponse(responseCode = "403", description = "Only the CLIENT who created the booking can cancel it")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @DELETE
    @Path("/{id}")
    public Response cancel(@PathParam("id") Long id) {
        Long clientId = Long.parseLong(jwt.getSubject());
        bookingService.cancel(id, clientId);
        return Response.noContent().build();
    }

    private BookingResponse toResponse(Booking b) {
        return new BookingResponse(
                b.id, b.client.id, b.client.name,
                b.coach.id, b.coach.name,
                b.availability.startsAt,
                b.status, b.notes, b.createdAt);
    }
}
