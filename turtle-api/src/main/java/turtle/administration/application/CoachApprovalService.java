package turtle.administration.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.coaching.domain.CoachProfile;
import turtle.coaching.domain.CoachStatus;

import java.util.List;

@ApplicationScoped
public class CoachApprovalService {

    public List<CoachProfile> listCoachesByStatus(String status) {
        if (status != null) {
            CoachStatus cs = CoachStatus.valueOf(status.toUpperCase());
            return CoachProfile.list("status", cs);
        }
        return CoachProfile.listAll();
    }

    @Transactional
    public CoachProfile approve(Long userId) {
        CoachProfile profile = CoachProfile.findByUserId(userId)
                .orElseThrow(() -> new WebApplicationException("Coach not found", 404));
        profile.status = CoachStatus.APPROVED;
        return profile;
    }

    @Transactional
    public CoachProfile reject(Long userId) {
        CoachProfile profile = CoachProfile.findByUserId(userId)
                .orElseThrow(() -> new WebApplicationException("Coach not found", 404));
        profile.status = CoachStatus.REJECTED;
        return profile;
    }
}
