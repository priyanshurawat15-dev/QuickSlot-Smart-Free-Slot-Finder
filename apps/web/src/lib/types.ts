export type Role =
  | "STUDENT"
  | "FACULTY"
  | "CLUB_HEAD"
  | "COORDINATOR"
  | "ALLOCATION_FACULTY"
  | "HOD"
  | "ADMIN";
export interface User {
  id: string;
  full_name: string;
  email: string;
  role: Role;
}
export interface Session {
  user: User;
  csrf_token: string;
}
export interface Room {
  id: string;
  code: string;
  kind: string;
  building: string;
  capacity: number | null;
  source_label?: string;
  floor?: string | null;
}
export interface BusyInterval {
  start_time: string;
  end_time: string;
  title?: string;
}
export interface AvailableRoom extends Room {
  state: "AVAILABLE" | "OCCUPIED" | "UNKNOWN";
  reason: string;
  busy_intervals: BusyInterval[];
}
export interface Availability {
  date: string;
  rooms: AvailableRoom[];
  coverage_notice: string;
}
export type RequestStatus =
  "PENDING" | "FORWARDED" | "APPROVED" | "REJECTED" | "WITHDRAWN" | "CANCELLED";
export interface HistoryEvent {
  action?: string;
  status?: string;
  reason?: string;
  actor_name?: string;
  created_at?: string;
  at?: string;
}
export interface BookingRequest {
  id: string;
  room_id: string;
  room_code: string;
  date: string;
  start_time: string;
  end_time: string;
  purpose: string;
  reason_category: ReasonCategory;
  club_id: string | null;
  club_name: string | null;
  attendance: number;
  status: RequestStatus;
  requester_name: string;
  history: HistoryEvent[];
}
export interface RequestInput {
  room_id: string;
  date: string;
  start_time: string;
  end_time: string;
  purpose: string;
  reason_category: ReasonCategory;
  club_id?: string | null;
  attendance: number;
}
export type ReasonCategory =
  "EXTRA_CLASS" | "WORKSHOP" | "HACKATHON" | "CLUB_MEETING" | "OTHER";
export interface RequestEligibility {
  can_request: boolean;
  club_required: boolean;
  clubs: { id: string; slug: string; name: string }[];
  reason_categories: { value: ReasonCategory; label: string }[];
}
export interface ScheduleSession {
  day_of_week: number;
  start_time: string;
  end_time: string;
  title: string;
  source_label?: string;
}
export interface RoomSchedule {
  room: Room;
  sessions: ScheduleSession[];
  coverage_notice: string;
}
export interface ImportIssue {
  code: string;
  message: string;
  session_id?: string;
  source?: Record<string, unknown>;
}
export interface ImportSession {
  id?: string;
  weekday: number;
  start_time: string;
  end_time: string;
  room_id: string | null;
  subject: string;
  faculty_ids: string[];
  source?: Record<string, unknown>;
}
export interface ImportVersion {
  version: number;
  id: string;
  scope_key: string;
  status: string;
  archived_at: string | null;
  valid_from: string;
  valid_until: string | null;
  filename: string;
  sha256?: string;
  sessions?: ImportSession[];
  issues?: ImportIssue[];
  coverage_room_ids: string[];
  publication_windows: {
    id: string;
    valid_from: string;
    valid_until: string | null;
  }[];
}
