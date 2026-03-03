package turtle.coach.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record TimeWindowResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime dailyStartTime,
        LocalTime dailyEndTime,
        int unitOfWorkMinutes,
        BigDecimal pricePerUnit,
        int priority
) {}
