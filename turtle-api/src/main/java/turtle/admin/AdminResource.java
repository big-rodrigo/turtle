package turtle.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.admin.dto.CoachStatusResponse;
import turtle.coach.CoachProfile;

import java.util.List;

@Tag(name = "Admin", description = "Admin-only operations for coach approval management")
@SecurityRequirement(name = "bearerAuth")
@Path("/admin/coaches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminResource {

    @Inject
    AdminService adminService;

    @Operation(summary = "List coaches by status", description = "Returns all coach profiles, optionally filtered by status (PENDING, APPROVED, REJECTED).")
    @APIResponse(responseCode = "200", description = "List of coach profiles",
            content = @Content(schema = @Schema(implementation = CoachStatusResponse.class)))
    @APIResponse(responseCode = "403", description = "ADMIN role required")
    @GET
    public List<CoachStatusResponse> list(@QueryParam("status") String status) {
        return adminService.listCoachesByStatus(status).stream()
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
        CoachProfile p = adminService.approve(userId);
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
        CoachProfile p = adminService.reject(userId);
        return new CoachStatusResponse(p.user.id, p.user.name, p.user.email, p.specialty, p.status.name());
    }
}
