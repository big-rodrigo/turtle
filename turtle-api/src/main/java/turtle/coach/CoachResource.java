package turtle.coach;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import turtle.coach.dto.AvailabilityRequest;
import turtle.coach.dto.AvailabilityResponse;
import turtle.coach.dto.CoachResponse;

import java.util.List;

@Path("/coaches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachResource {

    @Inject
    CoachService coachService;

    @Inject
    JsonWebToken jwt;

    @GET
    public List<CoachResponse> listCoaches() {
        return coachService.listCoaches().stream()
                .map(p -> new CoachResponse(p.user.id, p.user.name, p.specialty))
                .toList();
    }

    @GET
    @Path("/{id}/availability")
    public List<AvailabilityResponse> getAvailability(@PathParam("id") Long id) {
        return coachService.listFreeSlots(id).stream()
                .map(a -> new AvailabilityResponse(a.id, a.startsAt, a.endsAt, a.booked))
                .toList();
    }

    @POST
    @Path("/{id}/availability")
    @RolesAllowed("COACH")
    public Response addSlot(@PathParam("id") Long id, @Valid AvailabilityRequest req) {
        Long callerId = Long.parseLong(jwt.getSubject());
        if (!callerId.equals(id)) {
            throw new WebApplicationException("Forbidden", 403);
        }
        Availability slot = coachService.addSlot(id, req.startsAt(), req.endsAt());
        return Response.status(201)
                .entity(new AvailabilityResponse(slot.id, slot.startsAt, slot.endsAt, slot.booked))
                .build();
    }

    @DELETE
    @Path("/availability/{slotId}")
    @RolesAllowed("COACH")
    public Response deleteSlot(@PathParam("slotId") Long slotId) {
        Long coachId = Long.parseLong(jwt.getSubject());
        coachService.deleteSlot(slotId, coachId);
        return Response.noContent().build();
    }
}
