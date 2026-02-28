package turtle.coach;

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
import turtle.coach.dto.AvailabilityRequest;
import turtle.coach.dto.AvailabilityResponse;
import turtle.coach.dto.CoachResponse;

import java.util.List;

@Tag(name = "Coaches", description = "Browse coaches and manage availability slots")
@Path("/coaches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachResource {

    @Inject
    CoachService coachService;

    @Inject
    JsonWebToken jwt;

    @Operation(summary = "List all approved coaches", description = "Returns publicly accessible list of coaches whose status is APPROVED.")
    @APIResponse(responseCode = "200", description = "List of coaches",
            content = @Content(schema = @Schema(implementation = CoachResponse.class)))
    @GET
    public List<CoachResponse> listCoaches() {
        return coachService.listCoaches().stream()
                .map(p -> new CoachResponse(p.user.id, p.user.name, p.specialty))
                .toList();
    }

    @Operation(summary = "Get available slots for a coach", description = "Returns future, unbooked availability slots for the given coach.")
    @APIResponse(responseCode = "200", description = "List of free time slots",
            content = @Content(schema = @Schema(implementation = AvailabilityResponse.class)))
    @APIResponse(responseCode = "404", description = "Coach not found")
    @GET
    @Path("/{id}/availability")
    public List<AvailabilityResponse> getAvailability(@PathParam("id") Long id) {
        return coachService.listFreeSlots(id).stream()
                .map(a -> new AvailabilityResponse(a.id, a.startsAt, a.endsAt, a.booked))
                .toList();
    }

    @Operation(summary = "Add an availability slot (COACH)", description = "COACHes can add new time slots to their own schedule. The {id} must match the authenticated coach's user ID.")
    @APIResponse(responseCode = "201", description = "Slot created",
            content = @Content(schema = @Schema(implementation = AvailabilityResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error")
    @APIResponse(responseCode = "403", description = "COACH can only manage their own slots")
    @SecurityRequirement(name = "bearerAuth")
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

    @Operation(summary = "Delete an availability slot (COACH)", description = "COACHes can remove their own unbooked availability slots.")
    @APIResponse(responseCode = "204", description = "Slot deleted")
    @APIResponse(responseCode = "403", description = "COACH can only delete their own slots")
    @APIResponse(responseCode = "404", description = "Slot not found")
    @SecurityRequirement(name = "bearerAuth")
    @DELETE
    @Path("/availability/{slotId}")
    @RolesAllowed("COACH")
    public Response deleteSlot(@PathParam("slotId") Long slotId) {
        Long coachId = Long.parseLong(jwt.getSubject());
        coachService.deleteSlot(slotId, coachId);
        return Response.noContent().build();
    }
}
