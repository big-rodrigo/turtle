package turtle.coaching.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import turtle.coaching.domain.CoachProfile;
import turtle.coaching.domain.CoachStatus;

import java.util.List;

@ApplicationScoped
public class CoachQueryService {

    public List<CoachProfile> listCoaches() {
        return CoachProfile.list("status", CoachStatus.APPROVED);
    }

    public CoachProfile getProfileByUserId(Long coachId) {
        return CoachProfile.findByUserId(coachId)
                .filter(p -> p.status == CoachStatus.APPROVED)
                .orElseThrow(() -> new WebApplicationException("Coach not found", 404));
    }
}
