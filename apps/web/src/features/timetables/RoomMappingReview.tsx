import { useMemo, useState } from "react";
import { ArrowUpRight, CircleAlert } from "lucide-react";
import type { ImportSession, Room } from "../../lib/types";

const days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
interface MappedSession {
  session: ImportSession;
  index: number;
}
interface FlaggedSession extends MappedSession {
  reason: string;
}

function minutes(value: string) {
  const match = /^(\d{2}):(\d{2})/.exec(value);
  if (!match || Number(match[1]) > 23 || Number(match[2]) > 59) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}

function sourcePages(entries: MappedSession[]) {
  const pages = [
    ...new Set(
      entries
        .map(({ session }) => Number(session.source?.page))
        .filter((page) => Number.isInteger(page) && page > 0),
    ),
  ].sort((a, b) => a - b);
  return pages.length
    ? `Source pages ${pages.slice(0, 8).join(", ")}${pages.length > 8 ? ` +${pages.length - 8} more` : ""}`
    : "Source references in the session editor";
}

export function RoomMappingReview({
  sessions,
  rooms,
  busy,
  onReview,
}: {
  sessions: ImportSession[];
  rooms: Room[];
  busy: boolean;
  onReview(index: number): void;
}) {
  const [flagPage, setFlagPage] = useState(0);
  const mapping = useMemo(() => {
    const directory = new Map(rooms.map((room) => [room.id, room]));
    const groups = new Map<string, MappedSession[]>();
    const flags = new Map<number, string>();
    sessions.forEach((session, index) => {
      if (!session.room_id || !directory.has(session.room_id))
        flags.set(index, "Room not matched");
      else {
        const group = groups.get(session.room_id);
        if (group) group.push({ session, index });
        else groups.set(session.room_id, [{ session, index }]);
      }
      const start = minutes(session.start_time);
      const end = minutes(session.end_time);
      if (
        start === null ||
        end === null ||
        end <= start ||
        session.weekday < 0 ||
        session.weekday > 6
      )
        flags.set(index, "Check the class timing");
      if (!session.subject.trim()) flags.set(index, "Session name is missing");
    });
    for (const entries of groups.values()) {
      const sorted = entries
        .filter(({ session }) => {
          const start = minutes(session.start_time);
          const end = minutes(session.end_time);
          return start !== null && end !== null && end > start;
        })
        .sort(
          (a, b) =>
            a.session.weekday - b.session.weekday ||
            minutes(a.session.start_time)! - minutes(b.session.start_time)!,
        );
      for (let i = 0; i < sorted.length; i++) {
        const entry = sorted[i];
        for (let j = i + 1; j < sorted.length; j++) {
          const next = sorted[j];
          if (
            next.session.weekday !== entry.session.weekday ||
            minutes(next.session.start_time)! >=
              minutes(entry.session.end_time)!
          )
            break;
          flags.set(entry.index, "Overlapping classes in this room");
          flags.set(next.index, "Overlapping classes in this room");
        }
      }
    }
    const flagged: FlaggedSession[] = [...flags]
      .sort(([a], [b]) => a - b)
      .map(([index, reason]) => ({ index, reason, session: sessions[index] }));
    return {
      groups: [...groups]
        .map(([id, entries]) => ({ room: directory.get(id)!, entries }))
        .sort((a, b) => a.room.code.localeCompare(b.room.code)),
      flags,
      flagged,
    };
  }, [sessions, rooms]);
  const safePage = Math.min(
    flagPage,
    Math.max(0, Math.ceil(mapping.flagged.length / 10) - 1),
  );

  return (
    <section className="mapping-review" aria-label="Room mapping review">
      <div className="timetable-section-heading">
        <div>
          <h2>Check room mappings</h2>
          <p>
            Compare each room with the source timetable. Flagged rows need a
            closer look.
          </p>
        </div>
      </div>
      <div className="mapping-counts">
        <div>
          <span>Matched rooms</span>
          <strong>{mapping.groups.length}</strong>
        </div>
        <div>
          <span>Extracted classes</span>
          <strong>{sessions.length}</strong>
        </div>
        <div className={mapping.flagged.length ? "has-flags" : ""}>
          <span>Flagged rows</span>
          <strong>{mapping.flagged.length}</strong>
        </div>
      </div>
      <div className="mapping-card-grid">
        {mapping.groups.map(({ room, entries }) => {
          const flagged = entries.filter((entry) =>
            mapping.flags.has(entry.index),
          );
          return (
            <article
              key={room.id}
              className={`mapping-card ${flagged.length ? "mapping-card-flagged" : ""}`}
            >
              <div className="mapping-card-heading">
                <h3>
                  {room.code}{" "}
                  <small>({room.kind === "LT" ? "LT" : "CR"})</small>
                </h3>
                <span>
                  {flagged.length
                    ? `${flagged.length} flagged`
                    : "Room matched"}
                </span>
              </div>
              {room.floor && <p>{room.floor}</p>}
              <strong>{entries.length} classes</strong>
              <p>
                {[...new Set(entries.map(({ session }) => session.weekday))]
                  .sort()
                  .map((day) => days[day])
                  .filter(Boolean)
                  .join(" · ")}
              </p>
              <p className="mapping-source">{sourcePages(entries)}</p>
              <button
                className="text-link"
                disabled={busy}
                onClick={() => onReview(flagged[0]?.index ?? entries[0].index)}
              >
                Review mappings <ArrowUpRight size={15} />
              </button>
            </article>
          );
        })}
      </div>
      {mapping.flagged.length > 0 && (
        <section className="mapping-flags" aria-label="Flagged mappings">
          <h3>
            <CircleAlert size={18} />
            Flagged for review
          </h3>
          <p>
            Unmatched labels may refer to labs or other spaces outside the room
            register. Confirm the source before assigning a classroom.
          </p>
          <div className="mapping-flag-list">
            {mapping.flagged
              .slice(safePage * 10, safePage * 10 + 10)
              .map(({ session, index, reason }) => (
                <div key={session.id || index}>
                  <span>
                    <strong>{reason}</strong>
                    <small>
                      {session.subject || "Unnamed session"}
                      {session.source?.page
                        ? ` · Source page ${String(session.source.page)}`
                        : ""}
                    </small>
                  </span>
                  <button
                    className="text-link"
                    disabled={busy}
                    onClick={() => onReview(index)}
                  >
                    Review row {index + 1}
                    <ArrowUpRight size={15} />
                  </button>
                </div>
              ))}
          </div>
          {mapping.flagged.length > 10 && (
            <nav className="form-actions" aria-label="Flagged mapping pages">
              <button
                className="button button-outline button-small"
                disabled={busy || safePage === 0}
                onClick={() => setFlagPage(safePage - 1)}
              >
                Previous flags
              </button>
              <span>
                {safePage * 10 + 1}–
                {Math.min((safePage + 1) * 10, mapping.flagged.length)} of{" "}
                {mapping.flagged.length}
              </span>
              <button
                className="button button-outline button-small"
                disabled={busy || (safePage + 1) * 10 >= mapping.flagged.length}
                onClick={() => setFlagPage(safePage + 1)}
              >
                Next flags
              </button>
            </nav>
          )}
        </section>
      )}
      {!sessions.length && (
        <p>
          No class sessions were extracted. Check the import logs and source
          file.
        </p>
      )}
    </section>
  );
}
