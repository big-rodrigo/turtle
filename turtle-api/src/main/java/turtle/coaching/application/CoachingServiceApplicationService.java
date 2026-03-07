package turtle.coaching.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.coaching.api.dto.AvailabilityResponse;
import turtle.coaching.api.dto.CoachingServiceRequest;
import turtle.coaching.domain.Availability;
import turtle.coaching.domain.CoachingService;
import turtle.coaching.domain.TimeWindow;
import turtle.coaching.domain.service.CoachingServiceDomainService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CoachingServiceApplicationService {

    @Inject
    CoachingServiceDomainService coachingServiceDomainService;

    @Transactional
    public CoachingService create(Long coachId, CoachingServiceRequest req) {
        CoachingService svc = new CoachingService();
        svc.coach = turtle.identity.domain.AppUser.findById(coachId);
        svc.name = req.name();
        svc.description = req.description();
        svc.extras = resolveExtras(coachId, req.extraServiceIds(), null);
        svc.persist();
        return svc;
    }

    public List<CoachingService> listForCoach(Long coachId) {
        return CoachingService.findByCoach(coachId);
    }

    public List<AvailabilityResponse> getSlotsForService(Long serviceId, LocalDate date) {
        return Availability.findByServiceOnDate(serviceId, date).stream()
                .map(a -> new AvailabilityResponse(
                        a.id, a.startsAt, a.endsAt, a.status(),
                        a.timeWindow.service.id, a.timeWindow.service.name))
                .toList();
    }

    public CoachingService getById(Long serviceId) {
        CoachingService svc = CoachingService.findById(serviceId);
        if (svc == null) throw new WebApplicationException("Service not found", 404);
        return svc;
    }

    @Transactional
    public CoachingService update(Long serviceId, Long callerId, CoachingServiceRequest req) {
        CoachingService svc = CoachingService.findById(serviceId);
        if (svc == null) throw new WebApplicationException("Service not found", 404);
        if (!svc.coach.id.equals(callerId)) throw new WebApplicationException("Forbidden", 403);

        svc.name = req.name();
        if (req.description() != null) svc.description = req.description();

        if (req.extraServiceIds() != null) {
            svc.extras = resolveExtras(callerId, req.extraServiceIds(), serviceId);
        }
        return svc;
    }

    @Transactional
    public void delete(Long serviceId, Long callerId) {
        CoachingService svc = CoachingService.findById(serviceId);
        if (svc == null) throw new WebApplicationException("Service not found", 404);
        if (!svc.coach.id.equals(callerId)) throw new WebApplicationException("Forbidden", 403);

        if (TimeWindow.count("service.id", serviceId) > 0)
            throw new WebApplicationException("Cannot delete a service that has time windows referencing it", 409);

        long bookingCount = (long) CoachingService.getEntityManager()
                .createQuery("SELECT COUNT(b) FROM Booking b WHERE b.service.id = :sid")
                .setParameter("sid", serviceId)
                .getSingleResult();
        if (bookingCount > 0)
            throw new WebApplicationException("Cannot delete a service that has bookings", 409);

        long bookingExtraCount = (long) CoachingService.getEntityManager()
                .createQuery("SELECT COUNT(b) FROM Booking b JOIN b.extras e WHERE e.id = :sid")
                .setParameter("sid", serviceId)
                .getSingleResult();
        if (bookingExtraCount > 0)
            throw new WebApplicationException("Cannot delete a service that has been selected as an extra in bookings", 409);

        svc.extras.clear();
        svc.delete();
    }

    private List<CoachingService> resolveExtras(Long coachId, List<Long> extraIds, Long ownerServiceId) {
        if (extraIds == null || extraIds.isEmpty()) return new ArrayList<>();

        List<CoachingService> resolved = new ArrayList<>();
        for (Long extraId : extraIds) {
            CoachingService extra = CoachingService.findById(extraId);
            if (extra == null) throw new WebApplicationException("Extra service " + extraId + " not found", 404);
            resolved.add(extra);
        }

        coachingServiceDomainService.validateExtras(coachId, ownerServiceId, resolved);
        return resolved;
    }
}
