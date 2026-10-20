import type {
  Availability,
  BookingRequest,
  RequestInput,
  Room,
  RoomSchedule,
  Session,
  ImportSession,
  ImportVersion,
  RequestEligibility,
} from "./types";
let csrfToken = "";
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}
export async function send<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  if (options.method && options.method !== "GET" && csrfToken)
    headers.set("X-CSRF-Token", csrfToken);
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      headers,
      credentials: "include",
    });
  } catch {
    throw new ApiError(
      "The server is unavailable. Check that the QuickSlot API is running, then try again.",
      0,
    );
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      detail?: unknown;
    } | null;
    const validation = Array.isArray(body?.detail)
      ? body.detail
          .map((issue: unknown) => {
            if (typeof issue === "string") return issue;
            if (issue && typeof issue === "object") {
              const entry = issue as {
                msg?: string;
                message?: string;
                loc?: (string | number)[];
              };
              return [
                entry.loc?.slice(1).join(" / "),
                entry.msg || entry.message,
              ]
                .filter(Boolean)
                .join(": ");
            }
            return "";
          })
          .filter(Boolean)
          .slice(0, 8)
          .join("; ")
      : "";
    const detail =
      validation ||
      (typeof body?.detail === "string"
        ? body.detail
        : response.status === 401
          ? "Please sign in to continue."
          : `This action could not be completed (${response.status}). Please try again.`);
    throw new ApiError(detail, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
async function session(path: string, options?: RequestInit) {
  const result = await send<Session>(path, options);
  csrfToken = result.csrf_token;
  return result;
}
export const api = {
  me: () => session("/auth/me"),
  login: (email: string, password: string) =>
    session("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  logout: async () => {
    await send("/auth/logout", { method: "POST" });
    csrfToken = "";
  },
  rooms: () => send<Room[]>("/rooms"),
  availability: (params: URLSearchParams, signal?: AbortSignal) =>
    send<Availability>(`/availability?${params}`, { signal }),
  schedule: (id: string, date: string) =>
    send<RoomSchedule>(
      `/rooms/${encodeURIComponent(id)}/schedule?date=${date}`,
    ),
  requests: () => send<BookingRequest[]>("/requests"),
  createRequest: (input: RequestInput, key: string) =>
    send<BookingRequest>("/requests", {
      method: "POST",
      headers: { "Idempotency-Key": key },
      body: JSON.stringify(input),
    }),
  decide: (id: string, action: string, reason: string) =>
    send<BookingRequest>(`/requests/${id}/${action}`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    }),
  versions: (includeArchived = false) =>
    send<ImportVersion[]>(
      `/timetables${includeArchived ? "?include_archived=true" : ""}`,
    ),
  deleteTimetable: (id: string, expectedRevision: number) =>
    send<ImportVersion>(
      `/timetables/${id}?expected_revision=${expectedRevision}`,
      { method: "DELETE" },
    ),
  requestEligibility: () => send<RequestEligibility>("/requests/eligibility"),
  version: (id: string) => send<ImportVersion>(`/timetables/${id}`),
  upload: (
    file: File,
    scope_key: string,
    valid_from: string,
    valid_until: string | null,
  ) => {
    const body = new FormData();
    body.append("file", file);
    body.append("scope_key", scope_key);
    body.append("valid_from", valid_from);
    if (valid_until) body.append("valid_until", valid_until);
    body.append("coverage_room_ids", "[]");
    return send<ImportVersion>("/timetables", { method: "POST", body });
  },
  review: (
    id: string,
    sessions: ImportSession[],
    coverage_room_ids: string[],
    acknowledge_source: boolean,
    expected_revision: number,
  ) =>
    send<ImportVersion>(`/timetables/${id}/review`, {
      method: "PUT",
      body: JSON.stringify({
        sessions,
        coverage_room_ids,
        acknowledge_source,
        expected_revision,
      }),
    }),
  publish: (id: string, expected_revision: number) =>
    send<ImportVersion>(`/timetables/${id}/publish`, {
      method: "POST",
      body: JSON.stringify({ expected_revision }),
    }),
  retire: (id: string, expected_revision: number, effective_from: string) =>
    send<ImportVersion>(`/timetables/${id}/retire`, {
      method: "POST",
      body: JSON.stringify({ expected_revision, effective_from }),
    }),
  createRoom: (room: Omit<Room, "id">) =>
    send<Room>("/rooms", { method: "POST", body: JSON.stringify(room) }),
  updateRoomDetails: (
    id: string,
    details: {
      building: string;
      capacity: number | null;
      floor: string | null;
    },
  ) =>
    send<Room>(`/rooms/${encodeURIComponent(id)}/details`, {
      method: "PUT",
      body: JSON.stringify(details),
    }),
  faculty: () => send<{ id: string; name: string }[]>("/timetables/faculty"),
  createFaculty: (id: string, name: string) =>
    send<{ id: string; name: string }>("/timetables/faculty", {
      method: "POST",
      body: JSON.stringify({ id, name }),
    }),
  teachers: () => send<{ id: string; name: string }[]>("/timetables/teachers"),
  teacherSchedule: (id: string, date: string) =>
    send<{
      teacher: { id: string; name: string };
      date: string;
      notice: string;
      sessions: {
        start_time: string;
        end_time: string;
        subject: string;
        room_id: string | null;
        room_code: string | null;
        building: string | null;
      }[];
    }>(`/timetables/teachers/${encodeURIComponent(id)}/schedule?date=${date}`),
};
