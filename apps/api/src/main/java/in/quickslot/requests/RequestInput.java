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
    @Min(1) @Max(5000) int attendance,
    @Size(max = 32) String reasonCategory,
    @Size(max = 36) String clubId) {
  public RequestInput(
      String roomId,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      String purpose,
      int attendance) {
    this(roomId, date, startTime, endTime, purpose, attendance, null, null);
  }
}
