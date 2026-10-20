package in.quickslot.availability;

import in.quickslot.inventory.RoomController;
import in.quickslot.inventory.RoomRepository;
import in.quickslot.shared.ApiException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AvailabilityController {
  private final AvailabilityService availability;
  private final RoomRepository rooms;

  public AvailabilityController(AvailabilityService availability, RoomRepository rooms) {
    this.availability = availability;
    this.rooms = rooms;
  }

  @GetMapping("/availability")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, ?> search(
      @RequestParam LocalDate date,
      @RequestParam("start_time") LocalTime start,
      @RequestParam("end_time") LocalTime end) {
    AvailabilityService.validateWindow(start, end);
    return Map.of(
        "date",
        date,
        "rooms",
        rooms.publicRooms().stream().map(r -> availability.room(r, date, start, end)).toList(),
        "coverage_notice",
        AvailabilityService.NOTICE);
  }

  @GetMapping("/rooms/{id}/schedule")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, ?> schedule(@PathVariable String id, @RequestParam LocalDate date) {
    var room = rooms.get(id);
    if (!room.active() || !room.campusId().equals("gehu-hill-dehradun"))
      throw new ApiException(404, "Room not found.");
    LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    var sessions = new ArrayList<Map<String, ?>>();
    for (int day = 0; day < 7; day++) {
      LocalDate current = monday.plusDays(day);
      for (var interval : availability.busy(id, current)) {
        sessions.add(
            Map.of(
                "day_of_week",
                day,
                "start_time",
                interval.startTime(),
                "end_time",
                interval.endTime(),
                "title",
                interval.kind(),
                "source_label",
                "Published schedule"));
      }
    }
    return Map.of(
        "room",
        RoomController.publicView(room),
        "sessions",
        sessions,
        "coverage_notice",
        AvailabilityService.NOTICE);
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok", "service", "quickslot-api");
  }
}
