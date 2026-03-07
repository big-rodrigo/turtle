package turtle.booking.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import turtle.booking.domain.Booking;
import turtle.booking.domain.BookingStatus;
import turtle.coaching.domain.Availability;
import turtle.coaching.domain.CoachingService;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class BookingDomainService {

    public void validateSlots(List<Availability> slots) {
        Long coachId = slots.get(0).coach.id;
        if (slots.stream().anyMatch(a -> !a.coach.id.equals(coachId)))
            throw new WebApplicationException("All slots must belong to the same coach", 400);

        if (slots.stream().anyMatch(a -> a.booking != null))
            throw new WebApplicationException("One or more slots are already booked", 409);

        if (slots.stream().anyMatch(a -> a.startsAt.isBefore(LocalDateTime.now())))
            throw new WebApplicationException("One or more slots are in the past", 400);

        for (int i = 0; i < slots.size() - 1; i++) {
            if (!slots.get(i).endsAt.equals(slots.get(i + 1).startsAt))
                throw new WebApplicationException("Slots must be consecutive with no gaps", 400);
        }
    }

    public void validateSlotsMatchService(List<Availability> slots) {
        CoachingService service = slots.get(0).timeWindow != null ? slots.get(0).timeWindow.service : null;
        if (service == null)
            throw new WebApplicationException("Slots have no associated service", 400);
        for (Availability slot : slots) {
            if (slot.timeWindow == null || slot.timeWindow.service == null
                    || !slot.timeWindow.service.id.equals(service.id))
                throw new WebApplicationException("All slots must belong to the same service", 400);
        }
    }

    public void validateExtra(Long coachId, Long extraId, CoachingService extra, CoachingService mainService) {
        if (!extra.coach.id.equals(coachId))
            throw new WebApplicationException("Extra service " + extraId + " does not belong to this coach", 400);
        if (mainService.extras.stream().noneMatch(e -> e.id.equals(extraId)))
            throw new WebApplicationException(
                    "Service " + extraId + " is not an available extra for this booking's service", 400);
    }

    public void assertPending(Booking booking) {
        if (booking.status != BookingStatus.PENDING) {
            throw new WebApplicationException("Booking is not in PENDING status", 409);
        }
    }
}
