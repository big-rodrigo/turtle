package turtle.coach;

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
import turtle.coach.dto.AvailabilityResponse;
import turtle.coach.dto.CoachResponse;
import turtle.coach.dto.TimeWindowRequest;
import turtle.coach.dto.TimeWindowResponse;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Coaches", description = "Browse coaches and manage availability windows")
@Path("/coaches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachResource {

    @Inject
    CoachService coachService;

    @Inject
    TimeWindowService timeWindowService;

    @Inject
    SecurityIdentity identity;

    @Operation(summary = "List all approved coaches", description = "Returns publicly accessible list of coaches whose status is APPROVED.")
    @APIResponse(responseCode = "200", description = "List of coaches",
            content = @Content(schema = @Schema(implementation = CoachResponse.class)))
    @GET
    public List<CoachResponse> listCoaches() {
        return coachService.listCoaches().stream()
                .map(p -> new CoachResponse(p.user.id, p.user.name, p.specialty))
                .toList();
    }

    @Operation(summary = "List time windows for a coach", description = "Returns all time windows defined by the coach. Public endpoint.")
    @APIResponse(responseCode = "200", description = "List of time windows",
            content = @Content(schema = @Schema(implementation = TimeWindowResponse.class)))
    @GET
    @Path("/{id}/time-windows")
    public List<TimeWindowResponse> listTimeWindows(@PathParam("id") Long coachId) {
        return timeWindowService.listForCoach(coachId)
                .stream().map(this::toTimeWindowResponse).toList();
    }

    @Operation(summary = "Create a time window (COACH)", description = "COACHes define a recurring availability window. All slots within the window are immediately materialized as bookable availability records.")
    @APIResponse(responseCode = "201", description = "Time window created and slots materialized",
            content = @Content(schema = @Schema(implementation = TimeWindowResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error")
    @APIResponse(responseCode = "403", description = "COACH can only manage their own profile")
    @SecurityRequirement(name = "bearerAuth")
    @POST
    @Path("/{id}/time-windows")
    @RolesAllowed("COACH")
    public Response addTimeWindow(@PathParam("id") Long coachId, @Valid TimeWindowRequest req) {
        Long callerId = Long.parseLong(identity.getPrincipal().getName());
        if (!callerId.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
        TimeWindow tw = timeWindowService.create(coachId, req);
        return Response.status(201).entity(toTimeWindowResponse(tw)).build();
    }

    @Operation(summary = "Delete a time window (COACH)", description = "COACHes can delete their own time windows and all unbooked slots within them. Fails if any slots have active bookings.")
    @APIResponse(responseCode = "204", description = "Time window deleted")
    @APIResponse(responseCode = "403", description = "COACH can only delete their own time windows")
    @APIResponse(responseCode = "404", description = "Time window not found")
    @APIResponse(responseCode = "409", description = "Time window has active bookings")
    @SecurityRequirement(name = "bearerAuth")
    @DELETE
    @Path("/time-windows/{windowId}")
    @RolesAllowed("COACH")
    public Response deleteTimeWindow(@PathParam("windowId") Long windowId) {
        Long callerId = Long.parseLong(identity.getPrincipal().getName());
        timeWindowService.delete(windowId, callerId);
        return Response.noContent().build();
    }

    @Operation(summary = "Get availability slots for a coach on a date", description = "Returns all materialized availability slots for the coach on the given date, with their status (AVAILABLE, BOOKED, EXPIRED). Use the returned slot IDs to create bookings.")
    @APIResponse(responseCode = "200", description = "List of availability slots",
            content = @Content(schema = @Schema(implementation = AvailabilityResponse.class)))
    @GET
    @Path("/{id}/slots")
    public List<AvailabilityResponse> getAvailableSlots(
            @PathParam("id") Long coachId,
            @QueryParam("date") LocalDate date) {
        if (date == null) throw new WebApplicationException("Query parameter 'date' is required", 400);
        return timeWindowService.getSlotsForDate(coachId, date);
    }

    private TimeWindowResponse toTimeWindowResponse(TimeWindow tw) {
        return new TimeWindowResponse(
                tw.id, tw.startDate, tw.endDate,
                tw.dailyStartTime, tw.dailyEndTime,
                tw.unitOfWorkMinutes, tw.pricePerUnit, tw.priority);
    }
}
