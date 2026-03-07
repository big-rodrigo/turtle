package turtle.coaching.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import turtle.coaching.domain.CoachingService;

import java.util.List;

@ApplicationScoped
public class CoachingServiceDomainService {

    public void validateExtras(Long coachId, Long ownerServiceId, List<CoachingService> extras) {
        if (ownerServiceId != null && CoachingService.isUsedAsExtra(ownerServiceId)) {
            throw new WebApplicationException(
                    "Cannot add extras to a service that is itself used as an extra (max 1 level deep)", 400);
        }
        for (CoachingService extra : extras) {
            if (!extra.coach.id.equals(coachId)) {
                throw new WebApplicationException(
                        "Extra service " + extra.id + " does not belong to this coach", 400);
            }
            if (!extra.extras.isEmpty()) {
                throw new WebApplicationException(
                        "Extra service " + extra.id + " already has its own extras (max 1 level deep)", 400);
            }
        }
    }
}
