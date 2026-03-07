package turtle.coach;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.coach.dto.CoachingServiceRequest;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CoachingServiceMgmtService {

    @Transactional
    public CoachingService create(Long coachId, CoachingServiceRequest req) {
        CoachingService svc = new CoachingService();
        svc.coach = turtle.user.AppUser.findById(coachId);
        svc.name = req.name();
        svc.description = req.description();
        svc.extras = resolveExtras(coachId, req.extraServiceIds(), null);
        svc.persist();
        return svc;
    }

    public List<CoachingService> listForCoach(Long coachId) {
        return CoachingService.findByCoach(coachId);
    }

    public CoachingService getById(Long serviceId, Long coachId) {
        CoachingService svc = CoachingService.findById(serviceId);
        if (svc == null) throw new WebApplicationException("Service not found", 404);
        if (!svc.coach.id.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
        return svc;
    }

    @Transactional
    public CoachingService update(Long serviceId, Long coachId, CoachingServiceRequest req) {
        CoachingService svc = CoachingService.findById(serviceId);
        if (svc == null) throw new WebApplicationException("Service not found", 404);
        if (!svc.coach.id.equals(coachId)) throw new WebApplicationException("Forbidden", 403);

        svc.name = req.name();
        if (req.description() != null) svc.description = req.description();

        if (req.extraServiceIds() != null) {
            svc.extras = resolveExtras(coachId, req.extraServiceIds(), serviceId);
        }
        return svc;
    }

    @Transactional
    public void delete(Long serviceId, Long coachId) {
        CoachingService svc = CoachingService.findById(serviceId);
        if (svc == null) throw new WebApplicationException("Service not found", 404);
        if (!svc.coach.id.equals(coachId)) throw new WebApplicationException("Forbidden", 403);

        if (TimeWindow.count("service.id", serviceId) > 0)
            throw new WebApplicationException("Cannot delete a service that has time windows referencing it", 409);

        long bookingExtraCount = (long) turtle.booking.Booking.getEntityManager()
                .createQuery("SELECT COUNT(b) FROM Booking b JOIN b.extras e WHERE e.id = :sid")
                .setParameter("sid", serviceId)
                .getSingleResult();
        if (bookingExtraCount > 0)
            throw new WebApplicationException("Cannot delete a service that has been selected in bookings", 409);

        svc.extras.clear();
        svc.delete();
    }

    private List<CoachingService> resolveExtras(Long coachId, List<Long> extraIds, Long ownerServiceId) {
        if (extraIds == null || extraIds.isEmpty()) return new ArrayList<>();

        if (ownerServiceId != null && CoachingService.isUsedAsExtra(ownerServiceId))
            throw new WebApplicationException(
                    "Cannot add extras to a service that is itself used as an extra (max 1 level deep)", 400);

        List<CoachingService> resolved = new ArrayList<>();
        for (Long extraId : extraIds) {
            CoachingService extra = CoachingService.findById(extraId);
            if (extra == null) throw new WebApplicationException("Extra service " + extraId + " not found", 404);
            if (!extra.coach.id.equals(coachId))
                throw new WebApplicationException("Extra service " + extraId + " does not belong to this coach", 400);
            if (!extra.extras.isEmpty())
                throw new WebApplicationException(
                        "Extra service " + extraId + " already has its own extras (max 1 level deep)", 400);
            resolved.add(extra);
        }
        return resolved;
    }
}
