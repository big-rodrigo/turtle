package turtle.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.identity.api.dto.ClientProfileUpdateRequest;
import turtle.identity.domain.ClientProfile;
import turtle.identity.domain.ClientSocialLink;

@ApplicationScoped
public class ClientProfileApplicationService {

    @Transactional
    public ClientProfile updateProfile(Long userId, ClientProfileUpdateRequest req) {
        ClientProfile profile = ClientProfile.findByUserId(userId)
                .orElseThrow(() -> new WebApplicationException("Client not found", 404));

        profile.description = req.description();

        profile.socialLinks.clear();
        if (req.socialLinks() != null) {
            for (var linkReq : req.socialLinks()) {
                ClientSocialLink link = new ClientSocialLink();
                link.client = profile;
                link.type = linkReq.type();
                link.url = linkReq.url();
                link.label = linkReq.label();
                profile.socialLinks.add(link);
            }
        }

        return profile;
    }
}
