package turtle.booking;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import turtle.booking.dto.BookingResponse;
import turtle.booking.dto.CreateBookingRequest;
import turtle.user.UserRole;

import java.util.List;

@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class BookingResource {

    @Inject
    BookingService bookingService;

    @Inject
    JsonWebToken jwt;

    @POST
    @RolesAllowed("CLIENT")
    public Response create(CreateBookingRequest req) {
        Long clientId = Long.parseLong(jwt.getSubject());
        Booking booking = bookingService.create(clientId, req.availabilityId(), req.notes());
        return Response.status(201).entity(toResponse(booking)).build();
    }

    @GET
    public List<BookingResponse> list() {
        Long userId = Long.parseLong(jwt.getSubject());
        UserRole role = jwt.getGroups().contains("COACH") ? UserRole.COACH : UserRole.CLIENT;
        return bookingService.listForUser(userId, role).stream()
                .map(this::toResponse)
                .toList();
    }

    @GET
    @Path("/{id}")
    public BookingResponse get(@PathParam("id") Long id) {
        Long userId = Long.parseLong(jwt.getSubject());
        return toResponse(bookingService.getById(id, userId));
    }

    @PATCH
    @Path("/{id}/approve")
    @RolesAllowed("COACH")
    public BookingResponse approve(@PathParam("id") Long id) {
        Long coachId = Long.parseLong(jwt.getSubject());
        return toResponse(bookingService.approve(id, coachId));
    }

    @PATCH
    @Path("/{id}/reject")
    @RolesAllowed("COACH")
    public BookingResponse reject(@PathParam("id") Long id) {
        Long coachId = Long.parseLong(jwt.getSubject());
        return toResponse(bookingService.reject(id, coachId));
    }

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
