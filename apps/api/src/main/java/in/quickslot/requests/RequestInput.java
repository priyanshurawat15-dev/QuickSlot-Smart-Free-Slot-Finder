package in.quickslot.requests;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;

public record RequestInput(
    @NotBlank String roomId,
    @NotNull LocalDate date,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotBlank @Size(min = 3, max = 500) String purpose,
    @Min(1) @Max(5000) int attendance) {}
