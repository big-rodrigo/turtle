package turtle.coach;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CoachService {

    public List<CoachProfile> listCoaches() {
        return CoachProfile.listAll();
    }
}
