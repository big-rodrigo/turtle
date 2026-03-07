package turtle.coaching.application;

import jakarta.enterprise.context.ApplicationScoped;
import turtle.coaching.domain.CoachProfile;

import java.util.List;

@ApplicationScoped
public class CoachQueryService {

    public List<CoachProfile> listCoaches() {
        return CoachProfile.listAll();
    }
}
