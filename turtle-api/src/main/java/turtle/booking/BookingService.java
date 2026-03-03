package turtle.booking;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.booking.event.BookingApprovedEvent;
import turtle.booking.event.BookingCreatedEvent;
import turtle.booking.event.BookingRejectedEvent;
import turtle.coach.Availability;
import turtle.user.AppUser;
import turtle.user.UserRole;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class BookingService {

    @Inject
    Event<BookingCreatedEvent> bookingCreatedEvent;

    @Inject
    Event<BookingApprovedEvent> bookingApprovedEvent;

    @Inject
    Event<BookingRejectedEvent> bookingRejectedEvent;

    @Transactional
    public Booking create(Long clientId, List<Long> availabilityIds, String notes) {
        List<Availability> slots = availabilityIds.stream()
                .map(id -> {
                    Availability a = Availability.findById(id);
                    if (a == null) throw new WebApplicationException("Availability " + id + " not found", 404);
                    return a;
                })
                .sorted(Comparator.comparing(a -> a.startsAt))
                .toList();

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

        AppUser client = AppUser.findById(clientId);
        AppUser coach = AppUser.findById(coachId);

        Booking booking = new Booking();
        booking.client = client;
        booking.coach = coach;
        booking.status = BookingStatus.PENDING;
        booking.notes = notes;
        booking.createdAt = LocalDateTime.now();
        booking.persist();

        for (Availability slot : slots) {
            slot.booking = booking;
        }
        booking.slots = slots;

        bookingCreatedEvent.fire(new BookingCreatedEvent(booking));
        return booking;
    }

    @Transactional
    public Booking approve(Long bookingId, Long coachId) {
        Booking booking = findAndAssertCoachOwnership(bookingId, coachId);
        assertPending(booking);
        booking.status = BookingStatus.APPROVED;
        bookingApprovedEvent.fire(new BookingApprovedEvent(booking));
        return booking;
    }

    @Transactional
    public Booking reject(Long bookingId, Long coachId) {
        Booking booking = findAndAssertCoachOwnership(bookingId, coachId);
        assertPending(booking);
        booking.status = BookingStatus.REJECTED;
        booking.slots.forEach(s -> s.booking = null);
        bookingRejectedEvent.fire(new BookingRejectedEvent(booking));
        return booking;
    }

    @Transactional
    public void cancel(Long bookingId, Long clientId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) {
            throw new WebApplicationException("Booking not found", 404);
        }
        if (!booking.client.id.equals(clientId)) {
            throw new WebApplicationException("Forbidden", 403);
        }
        assertPending(booking);
        booking.status = BookingStatus.CANCELLED;
        booking.slots.forEach(s -> s.booking = null);
    }

    public List<Booking> listForUser(Long userId, UserRole role) {
        if (role == UserRole.COACH) {
            return Booking.list("coach.id", userId);
        }
        return Booking.list("client.id", userId);
    }

    public Booking getById(Long bookingId, Long userId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) {
            throw new WebApplicationException("Booking not found", 404);
        }
        if (!booking.client.id.equals(userId) && !booking.coach.id.equals(userId)) {
            throw new WebApplicationException("Forbidden", 403);
        }
        return booking;
    }

    private Booking findAndAssertCoachOwnership(Long bookingId, Long coachId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) {
            throw new WebApplicationException("Booking not found", 404);
        }
        if (!booking.coach.id.equals(coachId)) {
            throw new WebApplicationException("Forbidden", 403);
        }
        return booking;
    }

    private void assertPending(Booking booking) {
        if (booking.status != BookingStatus.PENDING) {
            throw new WebApplicationException("Booking is not in PENDING status", 409);
        }
    }
}
