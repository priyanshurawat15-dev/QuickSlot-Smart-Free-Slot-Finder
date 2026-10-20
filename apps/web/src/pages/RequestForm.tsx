import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ArrowLeft, ArrowUpRight } from "lucide-react";
import { api } from "../lib/api";
import type { Room, RequestEligibility, ReasonCategory } from "../lib/types";
import { campusToday } from "../lib/format";
import { ErrorText, Loading, Notice, PageHeading } from "../components/ui";
import { HourSlotSelect } from "../components/HourSlotSelect";
import { SelectField } from "../components/SelectField";
export function RequestForm() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [rooms, setRooms] = useState<Room[]>([]);
  const [roomId, setRoomId] = useState(params.get("room_id") || "");
  const [date, setDate] = useState(params.get("date") || campusToday());
  const [start, setStart] = useState(params.get("start_time") || "10:00");
  const [end, setEnd] = useState(params.get("end_time") || "11:00");
  const [purpose, setPurpose] = useState("");
  const [reason, setReason] = useState<ReasonCategory | "">("");
  const [clubId, setClubId] = useState("");
  const [eligibility, setEligibility] = useState<RequestEligibility | null>(
    null,
  );
  const [loading, setLoading] = useState(true);
  const [attendance, setAttendance] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const key = useRef(crypto.randomUUID());
  const lastPayload = useRef("");
  useEffect(() => {
    Promise.all([api.rooms(), api.requestEligibility()])
      .then(([inventory, access]) => {
        setRooms(inventory);
        setEligibility(access);
        if (access.club_required && access.clubs.length === 1)
          setClubId(access.clubs[0].id);
      })
      .catch(setError)
      .finally(() => setLoading(false));
  }, []);
  const selected = rooms.find((room) => room.id === roomId);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    if (end <= start) {
      setError(new Error("The end time must be after the start time."));
      return;
    }
    const category = eligibility?.reason_categories.find(
      (item) => item.value === reason,
    );
    if (!category) {
      setError(new Error("Choose a booking reason."));
      return;
    }
    if (reason === "OTHER" && purpose.trim().length < 3) {
      setError(new Error("Enter the reason for this room request."));
      return;
    }
    const payload = {
      room_id: roomId,
      date,
      start_time: start,
      end_time: end,
      purpose: purpose.trim() || category.label,
      reason_category: category.value,
      club_id: clubId || null,
      attendance: Number(attendance),
    };
    const serialized = JSON.stringify(payload);
    if (lastPayload.current && lastPayload.current !== serialized)
      key.current = crypto.randomUUID();
    lastPayload.current = serialized;
    setBusy(true);
    try {
      await api.createRequest(payload, key.current);
      navigate("/requests?created=1");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="container page narrow-page">
      <Link to="/" className="back-link">
        <ArrowLeft size={16} /> Back to rooms
      </Link>
      <PageHeading
        title="Request a room."
        text="Send a request to the allocation faculty. A request becomes a reservation only after approval."
      />
      {loading ? (
        <Loading label="Checking request access…" />
      ) : !eligibility?.can_request ? (
        <>
          {!!error && <ErrorText error={error} />}
          {!error && (
            <Notice>
              Join a club and have your membership verified before requesting a
              room for it.
            </Notice>
          )}
          <Link className="button button-primary" to="/clubs">
            Browse clubs
          </Link>
        </>
      ) : (
        <form className="request-sheet stack-form" onSubmit={submit}>
          <div className="form-section-title">
            <span>Room & time</span>
            <span>Campus time · IST</span>
          </div>
          <label>
            Room
            <SelectField
              aria-label="Room"
              required
              value={roomId}
              onValueChange={setRoomId}
              options={[
                { value: "", label: "Choose a classroom or lecture theatre" },
                ...rooms.map((room) => ({
                  value: room.id,
                  label: `${room.code} (${room.kind === "LT" ? "LT" : "CR"})${room.building ? ` · ${room.building}` : ""}${room.capacity !== null ? ` · ${room.capacity} seats` : ""}`,
                })),
              ]}
            />
          </label>
          <div className="form-columns">
            <label>
              Date
              <input
                type="date"
                required
                min={campusToday()}
                value={date}
                onChange={(e) => setDate(e.target.value)}
              />
            </label>
            <HourSlotSelect
              start={start}
              end={end}
              onChange={(from, until) => {
                setStart(from);
                setEnd(until);
              }}
            />
          </div>
          <div className="form-section-title">
            <span>Your plans</span>
          </div>
          <label>
            Booking reason
            <SelectField
              aria-label="Booking reason"
              required
              disabled={busy}
              value={reason}
              onValueChange={(value) => setReason(value as ReasonCategory)}
              options={[
                { value: "", label: "Choose a reason" },
                ...eligibility.reason_categories,
              ]}
            />
          </label>
          {(eligibility.club_required || eligibility.clubs.length > 0) && (
            <label>
              {eligibility.club_required
                ? "Request for club"
                : "Club (optional)"}
              <SelectField
                aria-label="Request for club"
                required={eligibility.club_required}
                disabled={busy}
                value={clubId}
                onValueChange={setClubId}
                options={[
                  {
                    value: "",
                    label: eligibility.club_required
                      ? "Choose your verified club"
                      : "Not a club request",
                  },
                  ...eligibility.clubs.map((club) => ({
                    value: club.id,
                    label: club.name,
                  })),
                ]}
              />
            </label>
          )}
          <label>
            {reason === "OTHER"
              ? "What is the room for?"
              : "Additional details (optional)"}
            <textarea
              required={reason === "OTHER"}
              disabled={busy}
              minLength={3}
              maxLength={500}
              rows={4}
              placeholder={
                reason === "OTHER"
                  ? "Enter your reason."
                  : "Course, section or activity details."
              }
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
            />
          </label>
          <label>
            Expected attendance
            <input
              type="number"
              required
              min={1}
              max={selected?.capacity || 5000}
              placeholder="Number of people"
              value={attendance}
              onChange={(e) => setAttendance(e.target.value)}
            />
          </label>
          <Notice>
            Booking hours are 08:00–18:00 IST. The coordinator can approve,
            reject, or forward your request to the HOD. The room is not held
            while a request is pending.
          </Notice>
          {!!error && <ErrorText error={error} />}
          <div className="form-actions">
            <Link to="/requests" className="text-link">
              Cancel
            </Link>
            <button
              className="button button-primary"
              disabled={busy || !rooms.length}
            >
              {busy ? "Sending request…" : "Send request"}
              <ArrowUpRight size={18} />
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
