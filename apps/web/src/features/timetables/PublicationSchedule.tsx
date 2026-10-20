import { useState, type FormEvent } from "react";
import type { ImportVersion } from "../../lib/types";
import { campusToday } from "../../lib/format";

export function timetableValidity(from: string, until: string | null) {
  return until ? `${from} to ${until}` : `From ${from} · no end date`;
}

export function PublicationSchedule({
  version,
  busy,
  onRetire,
}: {
  version: ImportVersion;
  busy: boolean;
  onRetire(date: string): Promise<boolean>;
}) {
  const [removing, setRemoving] = useState(false);
  const [removeFrom, setRemoveFrom] = useState(campusToday());
  const retire = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (await onRetire(removeFrom)) setRemoving(false);
  };

  return (
    <section className="coverage-review">
      <h3>Scheduled dates</h3>
      {version.publication_windows.length ? (
        <ul>
          {version.publication_windows.map((window) => (
            <li key={window.id}>
              {timetableValidity(window.valid_from, window.valid_until)}
            </li>
          ))}
        </ul>
      ) : (
        <p>No scheduled dates remain. The source is kept in history.</p>
      )}
      <p>
        A timetable without an end date stays active until an administrator
        removes it or publishes a replacement for this scope.
      </p>
      {version.publication_windows.length > 0 &&
        (removing ? (
          <form className="stack-form" onSubmit={retire}>
            <label>
              Remove from
              <input
                autoFocus
                required
                disabled={busy}
                type="date"
                value={removeFrom}
                onChange={(e) => setRemoveFrom(e.target.value)}
              />
            </label>
            <p>
              This version will no longer supply classes or room coverage from
              this date onward. Earlier dates and the original file remain
              available in history.
            </p>
            <div className="form-actions">
              <button
                className="button button-outline"
                type="button"
                disabled={busy}
                onClick={() => setRemoving(false)}
              >
                Keep timetable
              </button>
              <button className="button button-primary" disabled={busy}>
                {busy ? "Removing…" : "Confirm removal"}
              </button>
            </div>
          </form>
        ) : (
          <button
            className="button button-outline"
            disabled={busy}
            onClick={() => setRemoving(true)}
          >
            Remove from schedule
          </button>
        ))}
    </section>
  );
}
