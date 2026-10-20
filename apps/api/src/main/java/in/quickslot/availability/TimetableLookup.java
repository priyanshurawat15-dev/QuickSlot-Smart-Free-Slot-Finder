package in.quickslot.availability;

import java.time.LocalDate;
import java.util.List;

public interface TimetableLookup {
  List<BusyInterval> busy(String roomId, LocalDate date);

  boolean covered(String roomId, LocalDate date);
}
