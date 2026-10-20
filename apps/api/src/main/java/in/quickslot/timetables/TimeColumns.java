package in.quickslot.timetables;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TimeColumns {
  private static final Pattern CLOCK = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");

  private TimeColumns() {}

  static List<int[]> parse(List<String> headers, boolean allowTwelveHour) {
    List<int[]> result = new ArrayList<>();
    int previous = 0;
    for (String header : headers) {
      var matcher = CLOCK.matcher(header);
      List<Integer> values = new ArrayList<>();
      while (matcher.find()) {
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (hour > 23 || minute > 59) throw new IllegalArgumentException("Invalid clock time");
        int value = hour * 60 + minute;
        if (allowTwelveHour && value < previous && hour < 12) value += 720;
        if (value < previous || value >= 1440)
          throw new IllegalArgumentException("Time columns must progress within one day");
        previous = value;
        values.add(value);
      }
      if (values.size() != 2 || values.get(0) >= values.get(1))
        throw new IllegalArgumentException("Each column needs a valid start and end time");
      result.add(new int[] {values.get(0), values.get(1)});
    }
    return result;
  }

  static String clock(int minutes) {
    return "%02d:%02d".formatted(minutes / 60, minutes % 60);
  }

  static int day(String text) {
    return List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        .indexOf(text.strip().toUpperCase());
  }
}
