import { useState, type FormEvent } from "react";
import { ArrowUpRight, FileText, Upload } from "lucide-react";
import { campusToday } from "../../lib/format";

export function TimetableUpload({
  busy,
  onUpload,
}: {
  busy: boolean;
  onUpload(
    file: File,
    scope: string,
    from: string,
    until: string | null,
  ): Promise<void>;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [scope, setScope] = useState("");
  const [from, setFrom] = useState(campusToday());
  const [until, setUntil] = useState("");
  const [hasEndDate, setHasEndDate] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState("");
  const choose = (chosen: File | null) => {
    if (chosen && !/\.(pdf|docx)$/i.test(chosen.name)) {
      setError("Choose a PDF or Word timetable.");
      return;
    }
    setFile(chosen);
    setError("");
  };
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (file)
      await onUpload(file, scope.trim(), from, hasEndDate ? until : null);
  };
  return (
    <section className="timetable-upload">
      <h2>Upload a timetable</h2>
      <p>
        Room labels are matched to the room register. Check the mappings before
        publishing.
      </p>
      <form className="stack-form" onSubmit={submit}>
        <label
          className={`timetable-dropzone ${dragging ? "is-dragging" : ""}`}
          onDragOver={(event) => {
            event.preventDefault();
            if (!busy) setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            if (!busy) choose(event.dataTransfer.files[0] || null);
          }}
        >
          {file ? <FileText size={32} /> : <Upload size={32} />}
          <strong>{file ? file.name : "Drop your timetable here"}</strong>
          <span>
            {file ? "Choose another file" : "or choose a PDF or Word file"}
          </span>
          <input
            className="sr-only"
            aria-label="PDF or editable Word timetable"
            type="file"
            required={!file}
            disabled={busy}
            accept=".pdf,.docx"
            onChange={(event) => choose(event.target.files?.[0] || null)}
          />
        </label>
        {error && <p role="alert">{error}</p>}
        <label>
          Replacement scope
          <input
            required
            disabled={busy}
            placeholder="CSE / semester V / all sections"
            value={scope}
            onChange={(event) => setScope(event.target.value)}
          />
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={hasEndDate}
            disabled={busy}
            onChange={(event) => setHasEndDate(event.target.checked)}
          />
          Set an end date
        </label>
        <div className="form-columns">
          <label>
            Effective from
            <input
              required
              disabled={busy}
              type="date"
              value={from}
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          {hasEndDate && (
            <label>
              Valid until
              <input
                required
                disabled={busy}
                type="date"
                min={from}
                value={until}
                onChange={(event) => setUntil(event.target.value)}
              />
            </label>
          )}
        </div>
        {!hasEndDate && (
          <p className="timetable-help">
            No end date. Active until an administrator deletes it or publishes
            its replacement.
          </p>
        )}
        <div className="timetable-upload-actions">
          <a
            className="text-link"
            href="/templates/timetable-template.docx"
            download
          >
            Download Word template
          </a>
          <button className="button button-primary" disabled={busy}>
            {busy ? "Reading timetable…" : "Upload timetable"}
            <ArrowUpRight size={17} />
          </button>
        </div>
      </form>
    </section>
  );
}
