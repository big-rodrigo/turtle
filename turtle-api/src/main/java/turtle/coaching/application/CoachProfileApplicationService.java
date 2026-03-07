package turtle.coaching.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.coaching.api.dto.CoachProfileUpdateRequest;
import turtle.coaching.domain.CoachProfile;
import turtle.coaching.domain.CoachSocialLink;

@ApplicationScoped
public class CoachProfileApplicationService {

    @Transactional
    public CoachProfile updateProfile(Long coachId, CoachProfileUpdateRequest req) {
        CoachProfile profile = CoachProfile.findByUserId(coachId)
                .orElseThrow(() -> new WebApplicationException("Coach not found", 404));

        profile.description = req.description();
        profile.specialty = req.specialty();
        profile.pictureUrl = req.pictureUrl();

        profile.socialLinks.clear();
        if (req.socialLinks() != null) {
            for (var linkReq : req.socialLinks()) {
                CoachSocialLink link = new CoachSocialLink();
                link.coach = profile;
                link.type = linkReq.type();
                link.url = linkReq.url();
                link.label = linkReq.label();
                profile.socialLinks.add(link);
            }
        }

        return profile;
    }
}
