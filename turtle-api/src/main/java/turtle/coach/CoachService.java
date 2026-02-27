package turtle.coach;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.user.AppUser;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class CoachService {

    @Transactional
    public Availability addSlot(Long coachId, LocalDateTime startsAt, LocalDateTime endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new WebApplicationException("endsAt must be after startsAt", 400);
        }

        long overlaps = Availability.count(
                "coach.id = ?1 and booked = false and startsAt < ?2 and endsAt > ?3",
                coachId, endsAt, startsAt);
        if (overlaps > 0) {
            throw new WebApplicationException("Slot overlaps with an existing availability", 409);
        }

        AppUser coach = AppUser.findById(coachId);
        if (coach == null) {
            throw new WebApplicationException("Coach not found", 404);
        }

        Availability slot = new Availability();
        slot.coach = coach;
        slot.startsAt = startsAt;
        slot.endsAt = endsAt;
        slot.booked = false;
        slot.persist();
        return slot;
    }

    public List<Availability> listFreeSlots(Long coachId) {
        return Availability.findFreeByCoach(coachId);
    }

    @Transactional
    public void deleteSlot(Long slotId, Long coachId) {
        Availability slot = Availability.findById(slotId);
        if (slot == null) {
            throw new WebApplicationException("Slot not found", 404);
        }
        if (!slot.coach.id.equals(coachId)) {
            throw new WebApplicationException("Forbidden", 403);
        }
        if (slot.booked) {
            throw new WebApplicationException("Cannot delete a booked slot", 409);
        }
        slot.delete();
    }

    public List<CoachProfile> listCoaches() {
        return CoachProfile.listAll();
    }
}
