import { useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import { ErrorText } from "../../components/ui";
export function FacultyDirectory({
  faculty,
  onCreated,
  disabled = false,
}: {
  faculty: { id: string; name: string }[];
  onCreated(person: { id: string; name: string }): void;
  disabled?: boolean;
}) {
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError(null);
    try {
      const person = await api.createFaculty(
        String(data.get("id")).trim(),
        String(data.get("name")).trim(),
      );
      onCreated(person);
      form.reset();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <details className="faculty-directory">
      <summary>
        Faculty directory ({faculty.length}) · add a missing teacher
      </summary>
      <p>
        Faculty records identify timetable teachers. They do not create login
        accounts. Map teachers when known to include their classes in the staff
        timetable lookup. Room schedules can be reviewed without a faculty
        mapping.
      </p>
      <form className="stack-form" onSubmit={create}>
        <div className="form-columns">
          <label>
            Faculty ID
            <input
              name="id"
              disabled={disabled || busy}
              required
              maxLength={80}
              placeholder="Stable college faculty ID"
            />
          </label>
          <label>
            Full name
            <input
              name="name"
              required
              maxLength={150}
              disabled={disabled || busy}
            />
          </label>
        </div>
        {!!error && <ErrorText error={error} />}
        <button
          className="button button-outline button-small"
          disabled={disabled || busy}
        >
          Add faculty record
        </button>
      </form>
    </details>
  );
}
