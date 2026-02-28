package turtle.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import turtle.admin.dto.CoachStatusResponse;
import turtle.coach.CoachProfile;

import java.util.List;

@Path("/admin/coaches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminResource {

    @Inject
    AdminService adminService;

    @GET
    public List<CoachStatusResponse> list(@QueryParam("status") String status) {
        return adminService.listCoachesByStatus(status).stream()
                .map(p -> new CoachStatusResponse(
                        p.user.id, p.user.name, p.user.email, p.specialty, p.status.name()))
                .toList();
    }

    @PATCH
    @Path("/{userId}/approve")
    public CoachStatusResponse approve(@PathParam("userId") Long userId) {
        CoachProfile p = adminService.approve(userId);
        return new CoachStatusResponse(p.user.id, p.user.name, p.user.email, p.specialty, p.status.name());
    }

    @PATCH
    @Path("/{userId}/reject")
    public CoachStatusResponse reject(@PathParam("userId") Long userId) {
        CoachProfile p = adminService.reject(userId);
        return new CoachStatusResponse(p.user.id, p.user.name, p.user.email, p.specialty, p.status.name());
    }
}
