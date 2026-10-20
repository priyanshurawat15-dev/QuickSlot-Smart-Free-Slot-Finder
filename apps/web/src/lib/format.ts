export function campusToday() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
export function displayDate(date: string) {
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    weekday: "short",
  }).format(new Date(`${date}T12:00:00+05:30`));
}
export function time(value: string) {
  return value?.slice(0, 5) || "—";
}
export function roleName(role: string) {
  return (
    (
      {
        CLUB_HEAD: "Club head",
        ALLOCATION_FACULTY: "Room coordinator",
        COORDINATOR: "Room coordinator",
        HOD: "Head of department",
        ADMIN: "Administrator",
        FACULTY: "Faculty",
        STUDENT: "Student",
      } as Record<string, string>
    )[role] || role
  );
}
export function statusName(value: string) {
  return (
    (
      {
        AVAILABLE: "Free",
        OCCUPIED: "In use / booked",
        PENDING: "With coordinator",
        FORWARDED: "With HOD",
        APPROVED: "Approved",
        REJECTED: "Rejected",
        WITHDRAWN: "Withdrawn",
        CANCELLED: "Cancelled",
        DRAFT: "Draft",
        PUBLISHED: "Published",
        SUPERSEDED: "Previous version",
      } as Record<string, string>
    )[value] || value
  );
}
