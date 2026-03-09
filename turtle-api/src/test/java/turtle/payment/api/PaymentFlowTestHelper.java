package turtle.payment.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import turtle.booking.domain.Booking;
import turtle.booking.domain.BookingStatus;
import turtle.coaching.domain.*;
import turtle.identity.domain.AppUser;
import turtle.identity.domain.UserRole;
import turtle.payment.domain.Payment;
import turtle.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class PaymentFlowTestHelper {

    @Inject
    EntityManager em;

    private static final AtomicLong counter = new AtomicLong(System.nanoTime());

    public record TestBooking(Long bookingId, Long clientId, Long coachId) {}

    @Transactional
    public void ensureUserExists(Long id, String name, UserRole role) {
        Long existing = (Long) em.createNativeQuery("SELECT id FROM app_user WHERE id = :id")
                .setParameter("id", id)
                .getResultStream().findFirst().orElse(null);
        if (existing == null) {
            em.createNativeQuery(
                    "INSERT INTO app_user (id, name, email, password_hash, role) VALUES (:id, :name, :email, :hash, :role)")
                    .setParameter("id", id)
                    .setParameter("name", name)
                    .setParameter("email", name.toLowerCase().replace(" ", "-") + "-" + id + "@test.com")
                    .setParameter("hash", "$2a$10$dummyhash")
                    .setParameter("role", role.name())
                    .executeUpdate();
            // Advance sequence past this ID to avoid conflicts
            em.createNativeQuery("SELECT setval('app_user_id_seq', GREATEST(nextval('app_user_id_seq'), :id))")
                    .setParameter("id", id)
                    .getSingleResult();
        }
    }

    @Transactional
    public TestBooking createPendingBooking(BigDecimal pricePerUnit, Long clientId, Long coachId) {
        long n = counter.incrementAndGet();

        ensureUserExists(clientId, "Client " + clientId, UserRole.CLIENT);
        ensureUserExists(coachId, "Coach " + coachId, UserRole.COACH);

        AppUser client = em.find(AppUser.class, clientId);
        AppUser coach = em.find(AppUser.class, coachId);

        // Ensure coach profile
        Long profileCount = (Long) em.createQuery(
                "SELECT COUNT(p) FROM CoachProfile p WHERE p.user.id = :uid")
                .setParameter("uid", coachId)
                .getSingleResult();
        if (profileCount == 0) {
            CoachProfile profile = new CoachProfile();
            profile.user = coach;
            profile.status = CoachStatus.APPROVED;
            profile.persist();
        }

        CoachingService service = new CoachingService();
        service.coach = coach;
        service.name = "Service " + n;
        service.persist();

        LocalDate futureDate = LocalDate.now().plusDays(30);

        TimeWindow tw = new TimeWindow();
        tw.coach = coach;
        tw.startDate = futureDate;
        tw.endDate = futureDate;
        tw.dailyStartTime = LocalTime.of(9, 0);
        tw.dailyEndTime = LocalTime.of(11, 0);
        tw.unitOfWorkMinutes = 60;
        tw.pricePerUnit = pricePerUnit;
        tw.service = service;
        tw.persist();

        Availability slot1 = new Availability();
        slot1.coach = coach;
        slot1.startsAt = futureDate.atTime(9, 0);
        slot1.endsAt = futureDate.atTime(10, 0);
        slot1.timeWindow = tw;
        slot1.persist();

        Availability slot2 = new Availability();
        slot2.coach = coach;
        slot2.startsAt = futureDate.atTime(10, 0);
        slot2.endsAt = futureDate.atTime(11, 0);
        slot2.timeWindow = tw;
        slot2.persist();

        Booking booking = new Booking();
        booking.client = client;
        booking.coach = coach;
        booking.service = service;
        booking.status = BookingStatus.PENDING_PAYMENT;
        booking.notes = "test";
        booking.createdAt = LocalDateTime.now();
        booking.persist();

        slot1.booking = booking;
        slot2.booking = booking;

        return new TestBooking(booking.id, clientId, coachId);
    }

    @Transactional
    public TestBooking createPaidBooking(Long clientId, Long coachId) {
        TestBooking tb = createPendingBooking(new BigDecimal("100.00"), clientId, coachId);
        Booking booking = Booking.findById(tb.bookingId);
        booking.status = BookingStatus.AWAITING_COACH;

        Payment payment = new Payment();
        payment.booking = booking;
        payment.preferenceId = "PREF_PAID";
        payment.externalPaymentId = "EXT_PAY_PAID_" + counter.incrementAndGet();
        payment.status = PaymentStatus.APPROVED;
        payment.amount = new BigDecimal("200.00");
        payment.persist();

        return tb;
    }

    @Transactional
    public void createPaymentRecord(Long bookingId) {
        Booking booking = Booking.findById(bookingId);
        Payment payment = new Payment();
        payment.booking = booking;
        payment.status = PaymentStatus.PENDING;
        payment.amount = new BigDecimal("200.00");
        payment.persist();
    }

    @Transactional
    public void assertBookingStatus(Long bookingId, BookingStatus expected) {
        Booking booking = Booking.findById(bookingId);
        if (booking.status != expected) {
            throw new AssertionError("Expected " + expected + " but was " + booking.status);
        }
    }

    @Transactional
    public void assertPaymentStatus(Long bookingId, PaymentStatus expected) {
        Payment payment = Payment.findByBookingId(bookingId)
                .orElseThrow(() -> new AssertionError("No payment for booking " + bookingId));
        if (payment.status != expected) {
            throw new AssertionError("Expected " + expected + " but was " + payment.status);
        }
    }

    @Transactional
    public String getPaymentExternalId(Long bookingId) {
        return Payment.findByBookingId(bookingId)
                .map(p -> p.externalPaymentId)
                .orElse(null);
    }
}
