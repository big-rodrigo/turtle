package turtle.administration.api;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.administration.api.dto.CoachStatusResponse;
import turtle.administration.application.CoachApprovalService;
import turtle.coaching.api.dto.CoachProfileUpdateRequest;
import turtle.coaching.api.dto.CoachResponse;
import turtle.coaching.api.dto.SocialLinkResponse;
import turtle.coaching.application.CoachProfileApplicationService;
import turtle.coaching.domain.CoachProfile;

import java.util.List;

@Tag(name = "Admin", description = "Admin-only operations for coach approval management")
@SecurityRequirement(name = "bearerAuth")
@Path("/admin/coaches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminResource {

    @Inject
    CoachApprovalService coachApprovalService;

    @Inject
    CoachProfileApplicationService coachProfileService;

    @Operation(summary = "List coaches by status", description = "Returns all coach profiles, optionally filtered by status (PENDING, APPROVED, REJECTED).")
    @APIResponse(responseCode = "200", description = "List of coach profiles",
            content = @Content(schema = @Schema(implementation = CoachStatusResponse.class)))
    @APIResponse(responseCode = "403", description = "ADMIN role required")
    @GET
    public List<CoachStatusResponse> list(@QueryParam("status") String status) {
        return coachApprovalService.listCoachesByStatus(status).stream()
                .map(p -> new CoachStatusResponse(
                        p.user.id, p.user.name, p.user.email, p.specialty, p.status.name()))
                .toList();
    }

    @Operation(summary = "Approve a coach", description = "Changes the coach's status to APPROVED, allowing them to appear in the public coaches list.")
    @APIResponse(responseCode = "200", description = "Coach approved",
            content = @Content(schema = @Schema(implementation = CoachStatusResponse.class)))
    @APIResponse(responseCode = "403", description = "ADMIN role required")
    @APIResponse(responseCode = "404", description = "Coach not found")
    @PATCH
    @Path("/{userId}/approve")
    public CoachStatusResponse approve(@PathParam("userId") Long userId) {
        CoachProfile p = coachApprovalService.approve(userId);
        return new CoachStatusResponse(p.user.id, p.user.name, p.user.email, p.specialty, p.status.name());
    }

    @Operation(summary = "Reject a coach", description = "Changes the coach's status to REJECTED.")
    @APIResponse(responseCode = "200", description = "Coach rejected",
            content = @Content(schema = @Schema(implementation = CoachStatusResponse.class)))
    @APIResponse(responseCode = "403", description = "ADMIN role required")
    @APIResponse(responseCode = "404", description = "Coach not found")
    @PATCH
    @Path("/{userId}/reject")
    public CoachStatusResponse reject(@PathParam("userId") Long userId) {
        CoachProfile p = coachApprovalService.reject(userId);
        return new CoachStatusResponse(p.user.id, p.user.name, p.user.email, p.specialty, p.status.name());
    }

    @Operation(summary = "Update a coach's profile (ADMIN)", description = "Admins can update any coach's description, specialty, profile picture URL, and social links. Social links are fully replaced on each call.")
    @APIResponse(responseCode = "200", description = "Updated profile",
            content = @Content(schema = @Schema(implementation = CoachResponse.class)))
    @APIResponse(responseCode = "403", description = "ADMIN role required")
    @APIResponse(responseCode = "404", description = "Coach not found")
    @PUT
    @Path("/{userId}/profile")
    public CoachResponse updateProfile(@PathParam("userId") Long userId, @Valid CoachProfileUpdateRequest req) {
        CoachProfile p = coachProfileService.updateProfile(userId, req);
        List<SocialLinkResponse> links = p.socialLinks.stream()
                .map(l -> new SocialLinkResponse(l.id, l.type, l.url, l.label))
                .toList();
        return new CoachResponse(p.user.id, p.user.name, p.specialty, p.description, p.pictureUrl, links);
    }
}
