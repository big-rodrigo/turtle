package turtle.coach;

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
import turtle.coach.dto.CoachingServiceRequest;
import turtle.coach.dto.CoachingServiceResponse;

import java.util.List;

@Tag(name = "Coaching Services", description = "Manage coach-defined services and optional extras")
@Path("/coaches/{coachId}/services")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachingServiceResource {

    @Inject
    CoachingServiceMgmtService coachingServiceMgmtService;

    @Inject
    SecurityIdentity identity;

    @Operation(summary = "List services for a coach", description = "Returns all services defined by the coach. Public endpoint.")
    @APIResponse(responseCode = "200", description = "List of services",
            content = @Content(schema = @Schema(implementation = CoachingServiceResponse.class)))
    @GET
    public List<CoachingServiceResponse> list(@PathParam("coachId") Long coachId) {
        return coachingServiceMgmtService.listForCoach(coachId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Operation(summary = "Create a service (COACH)", description = "COACHes define a named service, optionally with a list of extra services clients can add.")
    @APIResponse(responseCode = "201", description = "Service created",
            content = @Content(schema = @Schema(implementation = CoachingServiceResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error or invalid extras")
    @APIResponse(responseCode = "403", description = "COACH can only manage their own services")
    @SecurityRequirement(name = "bearerAuth")
    @POST
    @RolesAllowed("COACH")
    public Response create(@PathParam("coachId") Long coachId, @Valid CoachingServiceRequest req) {
        assertCallerIs(coachId);
        CoachingService svc = coachingServiceMgmtService.create(coachId, req);
        return Response.status(201).entity(toResponse(svc)).build();
    }

    @Operation(summary = "Get a service by ID", description = "Returns service details including its optional extras. Public endpoint.")
    @APIResponse(responseCode = "200", description = "Service found",
            content = @Content(schema = @Schema(implementation = CoachingServiceResponse.class)))
    @APIResponse(responseCode = "403", description = "Service does not belong to this coach")
    @APIResponse(responseCode = "404", description = "Service not found")
    @GET
    @Path("/{serviceId}")
    public CoachingServiceResponse get(@PathParam("coachId") Long coachId,
                                       @PathParam("serviceId") Long serviceId) {
        return toResponse(coachingServiceMgmtService.getById(serviceId, coachId));
    }

    @Operation(summary = "Update a service (COACH)", description = "Update name, description, and/or extras list. Providing extraServiceIds fully replaces the current extras.")
    @APIResponse(responseCode = "200", description = "Service updated",
            content = @Content(schema = @Schema(implementation = CoachingServiceResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error or invalid extras")
    @APIResponse(responseCode = "403", description = "COACH can only manage their own services")
    @APIResponse(responseCode = "404", description = "Service not found")
    @SecurityRequirement(name = "bearerAuth")
    @PATCH
    @Path("/{serviceId}")
    @RolesAllowed("COACH")
    public CoachingServiceResponse update(@PathParam("coachId") Long coachId,
                                          @PathParam("serviceId") Long serviceId,
                                          @Valid CoachingServiceRequest req) {
        assertCallerIs(coachId);
        return toResponse(coachingServiceMgmtService.update(serviceId, coachId, req));
    }

    @Operation(summary = "Delete a service (COACH)", description = "Deletes a service. Fails if any time windows or booking extras reference it.")
    @APIResponse(responseCode = "204", description = "Service deleted")
    @APIResponse(responseCode = "403", description = "COACH can only manage their own services")
    @APIResponse(responseCode = "404", description = "Service not found")
    @APIResponse(responseCode = "409", description = "Service is referenced by time windows or bookings")
    @SecurityRequirement(name = "bearerAuth")
    @DELETE
    @Path("/{serviceId}")
    @RolesAllowed("COACH")
    public Response delete(@PathParam("coachId") Long coachId,
                           @PathParam("serviceId") Long serviceId) {
        assertCallerIs(coachId);
        coachingServiceMgmtService.delete(serviceId, coachId);
        return Response.noContent().build();
    }

    private void assertCallerIs(Long coachId) {
        Long callerId = Long.parseLong(identity.getPrincipal().getName());
        if (!callerId.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
    }

    private CoachingServiceResponse toResponse(CoachingService svc) {
        List<CoachingServiceResponse.ExtraServiceSummary> extras = svc.extras.stream()
                .map(e -> new CoachingServiceResponse.ExtraServiceSummary(e.id, e.name, e.description))
                .toList();
        return new CoachingServiceResponse(svc.id, svc.coach.id, svc.name, svc.description, extras);
    }
}
