package turtle.identity.api;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.identity.api.dto.ClientProfileResponse;
import turtle.identity.api.dto.ClientProfileUpdateRequest;
import turtle.identity.api.dto.ClientSocialLinkResponse;
import turtle.identity.application.ClientProfileApplicationService;
import turtle.identity.domain.ClientProfile;

import java.util.List;

@Tag(name = "Clients", description = "Client profile management")
@Path("/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClientResource {

    @Inject
    ClientProfileApplicationService clientProfileService;

    @Inject
    SecurityIdentity identity;

    @Operation(summary = "Get a client profile", description = "Returns the public profile of a client.")
    @APIResponse(responseCode = "200", description = "Client profile",
            content = @Content(schema = @Schema(implementation = ClientProfileResponse.class)))
    @APIResponse(responseCode = "404", description = "Client not found")
    @GET
    @Path("/{id}")
    public ClientProfileResponse getClient(@PathParam("id") Long clientId) {
        ClientProfile profile = ClientProfile.findByUserId(clientId)
                .orElseThrow(() -> new WebApplicationException("Client not found", 404));
        return toResponse(profile);
    }

    @Operation(summary = "Update own profile (CLIENT)", description = "CLIENTs can update their description and social links. Social links are fully replaced on each call.")
    @APIResponse(responseCode = "200", description = "Updated profile",
            content = @Content(schema = @Schema(implementation = ClientProfileResponse.class)))
    @APIResponse(responseCode = "403", description = "CLIENT can only update their own profile")
    @APIResponse(responseCode = "404", description = "Client not found")
    @SecurityRequirement(name = "bearerAuth")
    @PUT
    @Path("/{id}/profile")
    @RolesAllowed("CLIENT")
    public ClientProfileResponse updateProfile(@PathParam("id") Long clientId,
                                               @Valid ClientProfileUpdateRequest req) {
        Long callerId = Long.parseLong(identity.getPrincipal().getName());
        if (!callerId.equals(clientId)) throw new WebApplicationException("Forbidden", 403);
        return toResponse(clientProfileService.updateProfile(clientId, req));
    }

    private ClientProfileResponse toResponse(ClientProfile profile) {
        List<ClientSocialLinkResponse> links = profile.socialLinks.stream()
                .map(l -> new ClientSocialLinkResponse(l.id, l.type, l.url, l.label))
                .toList();
        return new ClientProfileResponse(profile.user.id, profile.user.name, profile.description, links);
    }
}
