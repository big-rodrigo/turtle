package turtle.coaching.api;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.coaching.api.dto.AvailabilityResponse;
import turtle.coaching.api.dto.CoachingServiceRequest;
import turtle.coaching.api.dto.CoachingServiceResponse;
import turtle.coaching.application.CoachingServiceApplicationService;
import turtle.coaching.domain.CoachingService;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Coaching Services", description = "Manage coach-defined services and optional extras")
@Path("/services/{serviceId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ServiceResource {

    @Inject
    CoachingServiceApplicationService coachingServiceAppService;

    @Inject
    SecurityIdentity identity;

    @Operation(summary = "Get a service by ID", description = "Returns service details including its optional extras. Public endpoint.")
    @APIResponse(responseCode = "200", description = "Service found",
            content = @Content(schema = @Schema(implementation = CoachingServiceResponse.class)))
    @APIResponse(responseCode = "404", description = "Service not found")
    @GET
    public CoachingServiceResponse get(@PathParam("serviceId") Long serviceId) {
        return toResponse(coachingServiceAppService.getById(serviceId));
    }

    @Operation(summary = "Update a service (COACH)", description = "Update name, description, and/or extras list. Providing extraServiceIds fully replaces the current extras.")
    @APIResponse(responseCode = "200", description = "Service updated",
            content = @Content(schema = @Schema(implementation = CoachingServiceResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error or invalid extras")
    @APIResponse(responseCode = "403", description = "Only the owning COACH can update this service")
    @APIResponse(responseCode = "404", description = "Service not found")
    @SecurityRequirement(name = "bearerAuth")
    @PATCH
    @RolesAllowed("COACH")
    public CoachingServiceResponse update(@PathParam("serviceId") Long serviceId,
                                          @Valid CoachingServiceRequest req) {
        Long callerId = Long.parseLong(identity.getPrincipal().getName());
        return toResponse(coachingServiceAppService.update(serviceId, callerId, req));
    }

    @Operation(summary = "Delete a service (COACH)", description = "Deletes a service. Fails if any time windows or bookings reference it.")
    @APIResponse(responseCode = "204", description = "Service deleted")
    @APIResponse(responseCode = "403", description = "Only the owning COACH can delete this service")
    @APIResponse(responseCode = "404", description = "Service not found")
    @APIResponse(responseCode = "409", description = "Service is referenced by time windows or bookings")
    @SecurityRequirement(name = "bearerAuth")
    @DELETE
    @RolesAllowed("COACH")
    public Response delete(@PathParam("serviceId") Long serviceId) {
        Long callerId = Long.parseLong(identity.getPrincipal().getName());
        coachingServiceAppService.delete(serviceId, callerId);
        return Response.noContent().build();
    }

    @Operation(summary = "Get available slots for a service on a date", description = "Returns all availability slots for this service on the given date. Public endpoint.")
    @APIResponse(responseCode = "200", description = "List of availability slots",
            content = @Content(schema = @Schema(implementation = AvailabilityResponse.class)))
    @GET
    @Path("/slots")
    public List<AvailabilityResponse> slots(@PathParam("serviceId") Long serviceId,
                                            @QueryParam("date") LocalDate date) {
        if (date == null) throw new WebApplicationException("Query parameter 'date' is required", 400);
        return coachingServiceAppService.getSlotsForService(serviceId, date);
    }

    private CoachingServiceResponse toResponse(CoachingService svc) {
        List<CoachingServiceResponse.ExtraServiceSummary> extras = svc.extras.stream()
                .map(e -> new CoachingServiceResponse.ExtraServiceSummary(e.id, e.name, e.description))
                .toList();
        return new CoachingServiceResponse(svc.id, svc.coach.id, svc.name, svc.description, extras);
    }
}
