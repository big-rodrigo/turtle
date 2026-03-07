package turtle.coaching.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@ApplicationScoped
public class TimeWindowDomainService {

    public void validateWindowParameters(LocalDate startDate, LocalDate endDate,
                                         LocalTime dailyStartTime, LocalTime dailyEndTime,
                                         int unitOfWorkMinutes) {
        if (endDate.isBefore(startDate)) {
            throw new WebApplicationException("endDate must be >= startDate", 400);
        }
        if (!dailyEndTime.isAfter(dailyStartTime)) {
            throw new WebApplicationException("dailyEndTime must be after dailyStartTime", 400);
        }
        long windowMinutes = Duration.between(dailyStartTime, dailyEndTime).toMinutes();
        if (unitOfWorkMinutes > windowMinutes) {
            throw new WebApplicationException("unitOfWorkMinutes exceeds the daily window duration", 400);
        }
    }
}
