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
import turtle.coaching.api.dto.CoachingServiceRequest;
import turtle.coaching.api.dto.CoachingServiceResponse;
import turtle.coaching.application.CoachingServiceApplicationService;
import turtle.coaching.domain.CoachingService;

import java.util.List;

@Tag(name = "Coaching Services", description = "Manage coach-defined services and optional extras")
@Path("/coaches/{coachId}/services")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachingServiceResource {

    @Inject
    CoachingServiceApplicationService coachingServiceAppService;

    @Inject
    SecurityIdentity identity;

    @Operation(summary = "List services for a coach", description = "Returns all services defined by the coach. Public endpoint.")
    @APIResponse(responseCode = "200", description = "List of services",
            content = @Content(schema = @Schema(implementation = CoachingServiceResponse.class)))
    @GET
    public List<CoachingServiceResponse> list(@PathParam("coachId") Long coachId) {
        return coachingServiceAppService.listForCoach(coachId).stream()
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
        Long callerId = Long.parseLong(identity.getPrincipal().getName());
        if (!callerId.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
        CoachingService svc = coachingServiceAppService.create(coachId, req);
        return Response.status(201).entity(toResponse(svc)).build();
    }

    private CoachingServiceResponse toResponse(CoachingService svc) {
        List<CoachingServiceResponse.ExtraServiceSummary> extras = svc.extras.stream()
                .map(e -> new CoachingServiceResponse.ExtraServiceSummary(e.id, e.name, e.description))
                .toList();
        return new CoachingServiceResponse(svc.id, svc.coach.id, svc.name, svc.description, extras);
    }
}
