package turtle.coach;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.coach.dto.AvailabilityResponse;
import turtle.coach.dto.TimeWindowRequest;
import turtle.coach.dto.TimeWindowResponse;
import turtle.user.AppUser;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@ApplicationScoped
public class TimeWindowService {

    @Transactional
    public TimeWindow create(Long coachId, TimeWindowRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new WebApplicationException("endDate must be >= startDate", 400);
        }
        if (!req.dailyEndTime().isAfter(req.dailyStartTime())) {
            throw new WebApplicationException("dailyEndTime must be after dailyStartTime", 400);
        }
        long windowMinutes = Duration.between(req.dailyStartTime(), req.dailyEndTime()).toMinutes();
        if (req.unitOfWorkMinutes() > windowMinutes) {
            throw new WebApplicationException("unitOfWorkMinutes exceeds the daily window duration", 400);
        }

        AppUser coach = AppUser.findById(coachId);
        TimeWindow tw = new TimeWindow();
        tw.coach = coach;
        tw.startDate = req.startDate();
        tw.endDate = req.endDate();
        tw.dailyStartTime = req.dailyStartTime();
        tw.dailyEndTime = req.dailyEndTime();
        tw.unitOfWorkMinutes = req.unitOfWorkMinutes();
        tw.pricePerUnit = req.pricePerUnit();
        tw.priority = req.priority();
        tw.persist();

        // Materialize all availability slots for every day in the window range
        LocalDate cursor = req.startDate();
        while (!cursor.isAfter(req.endDate())) {
            LocalTime slotStart = req.dailyStartTime();
            while (!slotStart.plusMinutes(req.unitOfWorkMinutes()).isAfter(req.dailyEndTime())) {
                Availability slot = new Availability();
                slot.coach = coach;
                slot.timeWindow = tw;
                slot.startsAt = LocalDateTime.of(cursor, slotStart);
                slot.endsAt = slot.startsAt.plusMinutes(req.unitOfWorkMinutes());
                slot.persist();
                slotStart = slotStart.plusMinutes(req.unitOfWorkMinutes());
            }
            cursor = cursor.plusDays(1);
        }

        return tw;
    }

    public List<TimeWindow> listForCoach(Long coachId) {
        return TimeWindow.findByCoach(coachId);
    }

    public List<AvailabilityResponse> getSlotsForDate(Long coachId, LocalDate date) {
        return Availability.findByCoachOnDate(coachId, date).stream()
                .map(a -> new AvailabilityResponse(a.id, a.startsAt, a.endsAt, a.status()))
                .toList();
    }

    @Transactional
    public void delete(Long windowId, Long coachId) {
        TimeWindow tw = TimeWindow.findById(windowId);
        if (tw == null) throw new WebApplicationException("Time window not found", 404);
        if (!tw.coach.id.equals(coachId)) throw new WebApplicationException("Forbidden", 403);

        boolean hasBookings = Availability.<Availability>list(
                "timeWindow.id = ?1 AND booking IS NOT NULL", windowId)
                .stream().anyMatch(a -> a.booking != null);
        if (hasBookings) throw new WebApplicationException(
                "Cannot delete a time window with active bookings", 409);

        Availability.delete("timeWindow.id", windowId);
        tw.delete();
    }
}
