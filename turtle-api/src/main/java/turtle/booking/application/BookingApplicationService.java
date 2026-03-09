package turtle.booking.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.booking.domain.Booking;
import turtle.booking.domain.BookingMaterial;
import turtle.booking.domain.BookingStatus;
import turtle.booking.domain.event.BookingCancelledEvent;
import turtle.booking.domain.event.BookingConfirmedEvent;
import turtle.booking.domain.event.BookingCreatedEvent;
import turtle.booking.domain.event.BookingRejectedEvent;
import turtle.booking.domain.service.BookingDomainService;
import turtle.coaching.domain.Availability;
import turtle.coaching.domain.CoachingService;
import turtle.identity.domain.AppUser;
import turtle.identity.domain.UserRole;
import turtle.payment.application.PaymentApplicationService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class BookingApplicationService {

    @Inject
    BookingDomainService bookingDomainService;

    @Inject
    PaymentApplicationService paymentService;

    @Inject
    Event<BookingCreatedEvent> bookingCreatedEvent;

    @Inject
    Event<BookingConfirmedEvent> bookingConfirmedEvent;

    @Inject
    Event<BookingRejectedEvent> bookingRejectedEvent;

    @Inject
    Event<BookingCancelledEvent> bookingCancelledEvent;

    @Transactional
    public Booking create(Long clientId, List<Long> availabilityIds, String notes, List<Long> extraServiceIds) {
        List<Availability> slots = availabilityIds.stream()
                .map(id -> {
                    Availability a = Availability.findById(id);
                    if (a == null) throw new WebApplicationException("Availability " + id + " not found", 404);
                    return a;
                })
                .sorted(Comparator.comparing(a -> a.startsAt))
                .toList();

        bookingDomainService.validateSlots(slots);
        bookingDomainService.validateSlotsMatchService(slots);

        Long serviceId = slots.get(0).timeWindow.service.id;
        CoachingService service = CoachingService.findById(serviceId);
        Long coachId = slots.get(0).coach.id;

        AppUser client = AppUser.findById(clientId);
        AppUser coach = AppUser.findById(coachId);

        Booking booking = new Booking();
        booking.service = service;
        booking.client = client;
        booking.coach = coach;
        booking.status = BookingStatus.PENDING_PAYMENT;
        booking.notes = notes;
        booking.createdAt = LocalDateTime.now();
        booking.persist();

        for (Availability slot : slots) {
            slot.booking = booking;
        }
        booking.slots = slots;

        if (extraServiceIds != null && !extraServiceIds.isEmpty()) {
            List<CoachingService> selectedExtras = new ArrayList<>();
            for (Long extraId : extraServiceIds) {
                CoachingService extra = CoachingService.findById(extraId);
                if (extra == null)
                    throw new WebApplicationException("Extra service " + extraId + " not found", 404);
                bookingDomainService.validateExtra(coachId, extraId, extra, service);
                selectedExtras.add(extra);
            }
            booking.extras = selectedExtras;
        }

        bookingCreatedEvent.fire(new BookingCreatedEvent(booking));
        return booking;
    }

    @Transactional
    public Booking confirm(Long bookingId, Long coachId) {
        Booking booking = findAndAssertCoachOwnership(bookingId, coachId);
        bookingDomainService.assertAwaitingCoach(booking);
        booking.status = BookingStatus.CONFIRMED;
        booking.resources.size(); // initialize lazy collection before TX closes
        bookingConfirmedEvent.fire(new BookingConfirmedEvent(booking));
        return booking;
    }

    @Transactional
    public Booking reject(Long bookingId, Long coachId) {
        Booking booking = findAndAssertCoachOwnership(bookingId, coachId);
        bookingDomainService.assertAwaitingCoach(booking);
        booking.status = BookingStatus.REJECTED;
        booking.slots.forEach(s -> s.booking = null);
        paymentService.refundForBooking(bookingId);
        booking.resources.size(); // initialize lazy collection before TX closes
        bookingRejectedEvent.fire(new BookingRejectedEvent(booking));
        return booking;
    }

    @Transactional
    public void cancel(Long bookingId, Long clientId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);
        if (!booking.client.id.equals(clientId)) throw new WebApplicationException("Forbidden", 403);

        if (booking.status != BookingStatus.PENDING_PAYMENT && booking.status != BookingStatus.AWAITING_COACH) {
            throw new WebApplicationException("Booking cannot be cancelled in its current status", 409);
        }

        // Refund if payment was already completed
        if (booking.status == BookingStatus.AWAITING_COACH) {
            paymentService.refundForBooking(bookingId);
        }

        booking.status = BookingStatus.CANCELLED;
        booking.slots.forEach(s -> s.booking = null);
        bookingCancelledEvent.fire(new BookingCancelledEvent(booking));
    }

    public List<Booking> listForUser(Long userId, UserRole role) {
        if (role == UserRole.COACH) {
            return Booking.list("coach.id", userId);
        }
        return Booking.list("client.id", userId);
    }

    public Booking getById(Long bookingId, Long userId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);
        if (!booking.client.id.equals(userId) && !booking.coach.id.equals(userId))
            throw new WebApplicationException("Forbidden", 403);
        return booking;
    }

    @Transactional
    public BookingMaterial addResource(Long bookingId, Long coachId, String title, String url, String description) {
        Booking booking = findAndAssertCoachOwnership(bookingId, coachId);
        if (booking.status != BookingStatus.CONFIRMED && booking.status != BookingStatus.AWAITING_COACH) {
            throw new WebApplicationException("Resources can only be added to confirmed or awaiting-coach bookings", 409);
        }
        BookingMaterial resource = new BookingMaterial();
        resource.booking = booking;
        resource.title = title;
        resource.url = url;
        resource.description = description;
        resource.persist();
        return resource;
    }

    @Transactional
    public void removeResource(Long bookingId, Long coachId, Long resourceId) {
        findAndAssertCoachOwnership(bookingId, coachId);
        BookingMaterial resource = BookingMaterial.findById(resourceId);
        if (resource == null) throw new WebApplicationException("Resource not found", 404);
        if (!resource.booking.id.equals(bookingId)) throw new WebApplicationException("Resource does not belong to this booking", 403);
        resource.delete();
    }

    public List<BookingMaterial> listResources(Long bookingId, Long userId) {
        Booking booking = getById(bookingId, userId);
        return BookingMaterial.list("booking.id", bookingId);
    }

    private Booking findAndAssertCoachOwnership(Long bookingId, Long coachId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);
        if (!booking.coach.id.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
        return booking;
    }
}
