import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowUpRight, Clock3, Search } from "lucide-react";
import { api } from "../lib/api";
import type { Availability } from "../lib/types";
import { campusToday, displayDate, time } from "../lib/format";
import { Empty, ErrorText, Loading, Notice } from "../components/ui";
import { RoomDirectory } from "../features/availability/RoomDirectory";
import { HourSlotSelect } from "../components/HourSlotSelect";
import { SelectField } from "../components/SelectField";

export function Finder() {
  const [params, setParams] = useSearchParams();
  const [date, setDate] = useState(params.get("date") || campusToday());
  const [start, setStart] = useState(params.get("start_time") || "10:00");
  const [end, setEnd] = useState(params.get("end_time") || "11:00");
  const [kind, setKind] = useState(params.get("kind") || "");
  const [capacity, setCapacity] = useState("");
  const [building, setBuilding] = useState("");
  const [floor, setFloor] = useState("");
  const [state, setState] = useState("");
  const [data, setData] = useState<Availability | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const query = params.toString();

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    const search = new URLSearchParams(query);
    if (!search.has("date")) search.set("date", campusToday());
    if (!search.has("start_time")) search.set("start_time", "10:00");
    if (!search.has("end_time")) search.set("end_time", "11:00");
    api
      .availability(search, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setError(failure);
          setData(null);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query]);

  const rooms = useMemo(
    () =>
      (data?.rooms || []).filter(
        (room) =>
          (!kind || room.kind === kind) &&
          (!building || room.building === building) &&
          (!floor || room.floor === floor) &&
          (!state || room.state === state) &&
          (!capacity ||
            (room.capacity !== null && room.capacity >= Number(capacity))),
      ),
    [data, kind, building, floor, state, capacity],
  );
  const buildings = [
    ...new Set(data?.rooms.map((room) => room.building).filter(Boolean) || []),
  ];
  const appliedStart = params.get("start_time") || "10:00";
  const floors = [
    ...new Set(
      data?.rooms
        .map((room) => room.floor)
        .filter((value): value is string => !!value) || [],
    ),
  ];
  const appliedEnd = params.get("end_time") || "11:00";

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (end <= start) {
      setError(new Error("Choose an end time after the start time."));
      return;
    }
    setParams({
      date,
      start_time: start,
      end_time: end,
      ...(kind ? { kind } : {}),
    });
  };

  return (
    <div className="container page availability-page">
      <div className="page-heading">
        <div>
          <h1>Room availability</h1>
          <p>Find classrooms and lecture theatres at Hill Dehradun.</p>
        </div>
        <Link className="button button-outline" to="/requests/new">
          Request a room <ArrowUpRight size={16} />
        </Link>
      </div>
      <div className="availability-workspace">
        <aside className="search-aside" aria-labelledby="search-title">
          <div className="panel-title">
            <h2 id="search-title">Search rooms</h2>
            <span>
              <Clock3 size={13} /> IST
            </span>
          </div>
          <form className="stack-form" onSubmit={submit}>
            <label>
              Date
              <input
                required
                aria-label="Date"
                type="date"
                value={date}
                onChange={(event) => setDate(event.target.value)}
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
            <label>
              Room type
              <SelectField
                aria-label="Room type"
                value={kind}
                onValueChange={setKind}
                options={[
                  { value: "", label: "Classrooms & LTs" },
                  { value: "CLASSROOM", label: "Classroom" },
                  { value: "LT", label: "Lecture theatre" },
                ]}
              />
            </label>
            <label>
              Minimum seats
              <input
                type="number"
                min="1"
                max="5000"
                placeholder="Any capacity"
                value={capacity}
                onChange={(event) => setCapacity(event.target.value)}
              />
            </label>
            <button className="button button-primary" disabled={loading}>
              <Search size={16} />
              Find a room
            </button>
          </form>
          <p className="physical-note">
            Check the room in person before settling in.
          </p>
        </aside>
        <section className="directory-panel" aria-labelledby="results-title">
          <div className="directory-heading">
            <div>
              <h2 id="results-title">Classrooms & lecture theatres</h2>
              <p>
                {data
                  ? `${displayDate(data.date)} · ${time(appliedStart)}–${time(appliedEnd)}`
                  : "Published room schedules"}
              </p>
            </div>
            {data && <span className="room-count">{rooms.length} rooms</span>}
          </div>
          {data && (
            <div
              className="availability-summary"
              aria-label="Room status totals"
            >
              {[
                { state: "AVAILABLE", label: "Free" },
                { state: "OCCUPIED", label: "In use / booked" },
              ].map((item) => (
                <div
                  className={`summary-card summary-${item.state.toLowerCase()}`}
                  key={item.state}
                >
                  <span>{item.label}</span>
                  <strong>
                    {rooms.filter((room) => room.state === item.state).length}
                  </strong>
                </div>
              ))}
            </div>
          )}
          <div className="directory-filters">
            <label>
              Building
              <SelectField
                aria-label="Building"
                value={building}
                disabled={!buildings.length}
                onValueChange={setBuilding}
                options={[
                  {
                    value: "",
                    label: buildings.length ? "All buildings" : "Not confirmed",
                  },
                  ...buildings.map((value) => ({ value, label: value })),
                ]}
              />
            </label>
            <label>
              Floor
              <SelectField
                aria-label="Floor"
                value={floor}
                disabled={!floors.length}
                onValueChange={setFloor}
                options={[
                  {
                    value: "",
                    label: floors.length ? "All floors" : "Not confirmed",
                  },
                  ...floors.map((value) => ({ value, label: value })),
                ]}
              />
            </label>
            <label>
              Availability
              <SelectField
                aria-label="Availability"
                value={state}
                onValueChange={setState}
                options={[
                  { value: "", label: "All rooms" },
                  { value: "AVAILABLE", label: "Free" },
                  { value: "OCCUPIED", label: "In use / booked" },
                ]}
              />
            </label>
          </div>
          {error ? (
            <ErrorText error={error} />
          ) : loading ? (
            <Loading label="Checking schedules…" />
          ) : rooms.length ? (
            <RoomDirectory
              rooms={rooms}
              date={data?.date || date}
              start={appliedStart}
              end={appliedEnd}
            />
          ) : (
            <Empty title="No rooms match these filters.">
              Try another room type, building or time.
            </Empty>
          )}
          {data?.coverage_notice && (
            <Notice>
              <strong>About these results</strong>
              <p>
                Availability follows published timetables and approved bookings.
                Check the room in person before use.
              </p>
            </Notice>
          )}
        </section>
      </div>
    </div>
  );
}
