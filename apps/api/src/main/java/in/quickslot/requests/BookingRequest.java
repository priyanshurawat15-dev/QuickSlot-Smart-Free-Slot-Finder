package in.quickslot.requests;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingRequest(
    String id,
    String requesterId,
    String roomId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    String purpose,
    int attendance,
    String status,
    String reasonCategory,
    String clubId,
    String clubName,
    boolean clubMembershipRequired) {}
