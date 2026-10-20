import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ArrowLeft, ArrowUpRight } from "lucide-react";
import { api } from "../lib/api";
import type { Room } from "../lib/types";
import { campusToday } from "../lib/format";
import { ErrorText, Notice, PageHeading } from "../components/ui";
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
  const [attendance, setAttendance] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const key = useRef(crypto.randomUUID());
  const lastPayload = useRef("");
  useEffect(() => {
    api.rooms().then(setRooms).catch(setError);
  }, []);
  const selected = rooms.find((room) => room.id === roomId);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    if (end <= start) {
      setError(new Error("The end time must be after the start time."));
      return;
    }
    const payload = {
      room_id: roomId,
      date,
      start_time: start,
      end_time: end,
      purpose: purpose.trim(),
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
          What is the room for?
          <textarea
            required
            minLength={5}
            maxLength={500}
            rows={4}
            placeholder="Tell the coordinator what you’re organising."
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
          reject, or forward your request to the HOD. The room is not held while
          a request is pending.
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
    </div>
  );
}
