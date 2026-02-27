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
    public Booking create(Long clientId, Long availabilityId, String notes) {
        Availability slot = Availability.findById(availabilityId);
        if (slot == null) {
            throw new WebApplicationException("Availability not found", 404);
        }
        if (slot.booked) {
            throw new WebApplicationException("Slot is already booked", 409);
        }

        AppUser client = AppUser.findById(clientId);
        slot.booked = true;

        Booking booking = new Booking();
        booking.client = client;
        booking.coach = slot.coach;
        booking.availability = slot;
        booking.status = BookingStatus.PENDING;
        booking.notes = notes;
        booking.createdAt = LocalDateTime.now();
        booking.persist();

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
        booking.availability.booked = false;
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
        booking.availability.booked = false;
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
