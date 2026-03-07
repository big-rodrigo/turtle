package turtle.coaching.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.coaching.api.dto.AvailabilityResponse;
import turtle.coaching.api.dto.PriorityUpdate;
import turtle.coaching.api.dto.TimeWindowRequest;
import turtle.coaching.domain.Availability;
import turtle.coaching.domain.CoachingService;
import turtle.coaching.domain.TimeWindow;
import turtle.coaching.domain.service.TimeWindowDomainService;
import turtle.identity.domain.AppUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class TimeWindowApplicationService {

    @Inject
    TimeWindowDomainService timeWindowDomainService;

    @Transactional
    public TimeWindow create(Long coachId, TimeWindowRequest req) {
        timeWindowDomainService.validateWindowParameters(
                req.startDate(), req.endDate(),
                req.dailyStartTime(), req.dailyEndTime(),
                req.unitOfWorkMinutes());

        AppUser coach = AppUser.findById(coachId);

        CoachingService service = CoachingService.findById(req.serviceId());
        if (service == null) throw new WebApplicationException("Service not found", 404);
        if (!service.coach.id.equals(coachId))
            throw new WebApplicationException("Service does not belong to this coach", 403);

        TimeWindow tw = new TimeWindow();
        tw.coach = coach;
        tw.startDate = req.startDate();
        tw.endDate = req.endDate();
        tw.dailyStartTime = req.dailyStartTime();
        tw.dailyEndTime = req.dailyEndTime();
        tw.unitOfWorkMinutes = req.unitOfWorkMinutes();
        tw.pricePerUnit = req.pricePerUnit();
        tw.priority = req.priority();
        tw.service = service;
        tw.persist();

        rematerializeSlots(coachId, req.startDate(), req.endDate());

        return tw;
    }

    public List<TimeWindow> listForCoach(Long coachId) {
        return TimeWindow.findByCoach(coachId);
    }

    public List<AvailabilityResponse> getSlotsForDate(Long coachId, LocalDate date) {
        return Availability.findByCoachOnDateWithService(coachId, date).stream()
                .map(a -> {
                    Long serviceId = (a.timeWindow != null && a.timeWindow.service != null)
                            ? a.timeWindow.service.id : null;
                    String serviceName = (a.timeWindow != null && a.timeWindow.service != null)
                            ? a.timeWindow.service.name : null;
                    return new AvailabilityResponse(a.id, a.startsAt, a.endsAt, a.status(), serviceId, serviceName);
                })
                .toList();
    }

    @Transactional
    public void reorder(Long coachId, List<PriorityUpdate> updates) {
        for (PriorityUpdate u : updates) {
            TimeWindow tw = TimeWindow.findById(u.id());
            if (tw == null) throw new WebApplicationException("Time window " + u.id() + " not found", 404);
            if (!tw.coach.id.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
            tw.priority = u.priority();
            tw.persist();
        }

        List<TimeWindow> allWindows = TimeWindow.findByCoach(coachId);
        if (allWindows.isEmpty()) return;
        LocalDate minDate = allWindows.stream().map(w -> w.startDate).min(Comparator.naturalOrder()).get();
        LocalDate maxDate = allWindows.stream().map(w -> w.endDate).max(Comparator.naturalOrder()).get();
        rematerializeSlots(coachId, minDate, maxDate);
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

        LocalDate startDate = tw.startDate;
        LocalDate endDate = tw.endDate;

        Availability.delete("timeWindow.id", windowId);
        tw.delete();

        // Restore slots for lower-priority windows that were suppressed by this one
        rematerializeSlots(coachId, startDate, endDate);
    }

    private void rematerializeSlots(Long coachId, LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveStart = startDate.isBefore(LocalDate.now()) ? LocalDate.now() : startDate;
        if (effectiveStart.isAfter(endDate)) return;

        for (LocalDate date = effectiveStart; !date.isAfter(endDate); date = date.plusDays(1)) {
            // Protect booked slot times
            List<LocalTime[]> claimed = new ArrayList<>();
            for (Availability booked : Availability.findBookedForCoachOnDate(coachId, date)) {
                claimed.add(new LocalTime[]{booked.startsAt.toLocalTime(), booked.endsAt.toLocalTime()});
            }

            // Wipe all unbooked future slots for this coach on this date
            Availability.deleteUnbookedFutureForCoachOnDate(coachId, date);

            // Re-create slots in priority order (0 = highest priority)
            for (TimeWindow tw : TimeWindow.findByCoachOverlappingDates(coachId, date, date)) {
                LocalTime slotStart = tw.dailyStartTime;
                while (!slotStart.plusMinutes(tw.unitOfWorkMinutes).isAfter(tw.dailyEndTime)) {
                    LocalTime slotEnd = slotStart.plusMinutes(tw.unitOfWorkMinutes);
                    if (noOverlap(claimed, slotStart, slotEnd)) {
                        Availability slot = new Availability();
                        slot.coach = tw.coach;
                        slot.timeWindow = tw;
                        slot.startsAt = LocalDateTime.of(date, slotStart);
                        slot.endsAt = LocalDateTime.of(date, slotEnd);
                        slot.persist();
                        claimed.add(new LocalTime[]{slotStart, slotEnd});
                    }
                    slotStart = slotEnd;
                }
            }
        }
    }

    private boolean noOverlap(List<LocalTime[]> claimed, LocalTime start, LocalTime end) {
        return claimed.stream().noneMatch(c -> start.isBefore(c[1]) && c[0].isBefore(end));
    }
}
