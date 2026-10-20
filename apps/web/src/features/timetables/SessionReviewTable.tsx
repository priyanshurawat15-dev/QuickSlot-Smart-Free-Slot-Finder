import type { ImportSession, Room } from "../../lib/types";
import { time } from "../../lib/format";
import { SelectField } from "../../components/SelectField";
const days = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
];
interface Props {
  sessions: ImportSession[];
  rooms: Room[];
  faculty: { id: string; name: string }[];
  editable: boolean;
  update(index: number, patch: Partial<ImportSession>): void;
  onRemove(index: number): void;
}
export function SessionReviewTable({
  sessions,
  rooms,
  faculty,
  editable,
  update,
  onRemove,
}: Props) {
  return (
    <div className="review-table-wrap">
      <table className="data-table review-table">
        <caption>Extracted sessions · {sessions.length} rows</caption>
        <thead>
          <tr>
            <th>Day</th>
            <th>From</th>
            <th>Until</th>
            <th>Room</th>
            <th>Subject / session</th>
            <th>Faculty</th>
            <th>Source</th>
            <th>
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {sessions.map((session, i) => (
            <tr key={i}>
              <td>
                <SelectField
                  aria-label={`Day for row ${i + 1}`}
                  disabled={!editable}
                  value={String(session.weekday)}
                  onValueChange={(value) =>
                    update(i, { weekday: Number(value) })
                  }
                  options={days.map((day, d) => ({
                    value: String(d),
                    label: day,
                  }))}
                />
              </td>
              <td>
                <input
                  aria-label={`Start for row ${i + 1}`}
                  disabled={!editable}
                  type="time"
                  value={time(session.start_time)}
                  onChange={(e) => update(i, { start_time: e.target.value })}
                />
              </td>
              <td>
                <input
                  aria-label={`End for row ${i + 1}`}
                  disabled={!editable}
                  type="time"
                  value={time(session.end_time)}
                  onChange={(e) => update(i, { end_time: e.target.value })}
                />
              </td>
              <td>
                <SelectField
                  aria-label={`Room for row ${i + 1}`}
                  disabled={!editable}
                  value={session.room_id || ""}
                  onValueChange={(value) =>
                    update(i, { room_id: value || null })
                  }
                  options={[
                    { value: "", label: "Unresolved" },
                    ...rooms.map((room) => ({
                      value: room.id,
                      label: `${room.code} (${room.kind === "LT" ? "LT" : "CR"})`,
                    })),
                  ]}
                />
              </td>
              <td>
                <input
                  aria-label={`Subject for row ${i + 1}`}
                  disabled={!editable}
                  value={session.subject}
                  onChange={(e) => update(i, { subject: e.target.value })}
                />
              </td>
              <td>
                <div
                  className="faculty-choices"
                  role="group"
                  aria-label={`Faculty for row ${i + 1}`}
                >
                  {faculty.map((person) => (
                    <label key={person.id}>
                      <input
                        type="checkbox"
                        disabled={!editable}
                        checked={session.faculty_ids.includes(person.id)}
                        onChange={(event) =>
                          update(i, {
                            faculty_ids: event.target.checked
                              ? [...session.faculty_ids, person.id]
                              : session.faculty_ids.filter(
                                  (id) => id !== person.id,
                                ),
                          })
                        }
                      />
                      {person.name}
                    </label>
                  ))}
                  {!faculty.length && <span>No faculty in directory</span>}
                </div>
              </td>
              <td>
                <small className="source-text">
                  {String(
                    session.source?.text ||
                      (session.source?.page
                        ? `Page ${session.source.page}`
                        : "Manual entry"),
                  )}
                </small>
              </td>
              <td>
                {editable && (
                  <button
                    className="text-link"
                    aria-label={`Remove row ${i + 1}`}
                    onClick={() => onRemove(i)}
                  >
                    Remove
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
