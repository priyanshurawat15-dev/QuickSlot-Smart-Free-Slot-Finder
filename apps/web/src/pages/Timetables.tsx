import { SessionReviewTable } from "../features/timetables/SessionReviewTable";
import { useEffect, useState, type FormEvent } from "react";
import {
  FileText,
  Upload,
  ArrowUpRight,
  Check,
  ChevronLeft,
} from "lucide-react";
import { FacultyDirectory } from "../features/timetables/FacultyDirectory";
import { api } from "../lib/api";
import type { ImportSession, ImportVersion, Room } from "../lib/types";
import { campusToday } from "../lib/format";
import {
  Empty,
  ErrorText,
  Loading,
  Notice,
  PageHeading,
  Status,
} from "../components/ui";
export function Timetables() {
  const [faculty, setFaculty] = useState<{ id: string; name: string }[]>([]);
  const [versions, setVersions] = useState<ImportVersion[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [selected, setSelected] = useState<ImportVersion | null>(null);
  const [sessions, setSessions] = useState<ImportSession[]>([]);
  const [coverage, setCoverage] = useState<string[]>([]);
  const [acknowledge, setAcknowledge] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [scope, setScope] = useState("");
  const [from, setFrom] = useState(campusToday());
  const [until, setUntil] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState("");
  const load = async () => {
    const [list, inventory, people] = await Promise.all([
      api.versions(),
      api.rooms(),
      api.faculty(),
    ]);
    setVersions(list);
    setRooms(inventory);
    setFaculty(people);
  };
  useEffect(() => {
    load()
      .catch(setError)
      .finally(() => setLoading(false));
  }, []);
  const open = async (id: string) => {
    setBusy(true);
    setError(null);
    setAcknowledge(false);
    try {
      const detail = await api.version(id);
      setSelected(detail);
      setDirty(false);
      setSessions(detail.sessions || []);
      setCoverage(detail.coverage_room_ids || []);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  const upload = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!file) return;
    setBusy(true);
    setError(null);
    setSuccess("");
    try {
      const detail = await api.upload(file, scope.trim(), from, until);
      await load();
      setSelected(detail);
      setDirty(false);
      setSessions(detail.sessions || []);
      setCoverage(detail.coverage_room_ids || []);
      setAcknowledge(false);
      setSuccess(
        "File uploaded as a draft. Review every extracted session before publication.",
      );
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  const review = async () => {
    if (!selected) return;
    setBusy(true);
    setError(null);
    try {
      const result = await api.review(
        selected.id,
        sessions,
        coverage,
        acknowledge,
        selected.version,
      );
      setSelected(result);
      setDirty(false);
      setSessions(result.sessions || []);
      setSuccess("Review saved. Check the remaining issues before publishing.");
      await load();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  const publish = async () => {
    if (!selected) return;
    setBusy(true);
    setError(null);
    try {
      const result = await api.publish(selected.id, selected.version);
      setSelected(result);
      setDirty(false);
      setSuccess(
        "Timetable published. The prior version is retained in history.",
      );
      await load();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  const update = (index: number, patch: Partial<ImportSession>) => {
    setSessions((current) =>
      current.map((session, i) =>
        i === index ? { ...session, ...patch } : session,
      ),
    );
    setAcknowledge(false);
    setDirty(true);
  };
  return (
    <div className="container page">
      <PageHeading
        title="Timetables."
        text="Upload, review and publish timetable versions."
      />
      {!!error && <ErrorText error={error} />}{" "}
      {success && <Notice tone="success">{success}</Notice>}
      {selected ? (
        <>
          <button
            className="back-link"
            onClick={() => {
              setSelected(null);
              setSuccess("");
            }}
          >
            <ChevronLeft size={16} />
            All timetables
          </button>
          <div className="version-heading">
            <div>
              <h2>{selected.filename}</h2>
              <p>
                {selected.scope_key} · {selected.valid_from} to{" "}
                {selected.valid_until}
              </p>
            </div>
            <Status value={selected.status} />
          </div>
          <a
            className="text-link source-download"
            href={`/api/timetables/${selected.id}/source`}
            target="_blank"
            rel="noreferrer"
          >
            Download original source <ArrowUpRight size={15} />
          </a>
          <Notice>
            Check the original file alongside these sessions. Scope identifies
            the course, semester and section set this version replaces. Other
            timetable scopes remain active.
          </Notice>
          {!!selected.issues?.length && (
            <div className="issue-list">
              <h3>Resolve before publication</h3>
              {selected.issues.map((issue, i) => (
                <p key={i}>
                  <strong>{issue.code.replaceAll("_", " ")}</strong>{" "}
                  {issue.message}
                </p>
              ))}
            </div>
          )}
          <FacultyDirectory
            faculty={faculty}
            onCreated={(person) =>
              setFaculty((current) => [...current, person])
            }
          />
          <SessionReviewTable
            sessions={sessions}
            rooms={rooms}
            faculty={faculty}
            editable={selected.status !== "PUBLISHED"}
            update={update}
            onRemove={(index) => {
              setSessions((current) => current.filter((_, i) => i !== index));
              setAcknowledge(false);
              setDirty(true);
            }}
          />
          {selected.status !== "PUBLISHED" && (
            <>
              <button
                className="button button-outline button-small"
                onClick={() => {
                  setSessions((value) => [
                    ...value,
                    {
                      weekday: 0,
                      start_time: "09:00",
                      end_time: "10:00",
                      room_id: null,
                      subject: "",
                      faculty_ids: [],
                    },
                  ]);
                  setAcknowledge(false);
                  setDirty(true);
                }}
              >
                Add session
              </button>
              <section className="coverage-review">
                <h3>Rooms with complete schedule coverage</h3>
                <p>
                  Select a room only if all courses using it are covered by
                  verified schedules. Seeing a room in this file is not enough.
                </p>
                <div className="coverage-checks">
                  {rooms.map((room) => (
                    <label key={room.id}>
                      <input
                        type="checkbox"
                        checked={coverage.includes(room.id)}
                        onChange={(e) => {
                          setCoverage((current) =>
                            e.target.checked
                              ? [...current, room.id]
                              : current.filter((id) => id !== room.id),
                          );
                          setAcknowledge(false);
                          setDirty(true);
                        }}
                      />
                      {room.code}
                      <small>{room.building}</small>
                    </label>
                  ))}
                </div>
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={acknowledge}
                    onChange={(e) => setAcknowledge(e.target.checked)}
                  />
                  I checked the source, session mappings, dates and coverage
                  claims.
                </label>
                <div className="form-actions">
                  <button
                    className="button button-outline"
                    disabled={busy || !acknowledge}
                    onClick={review}
                  >
                    <Check size={17} />
                    Save verified review
                  </button>
                  <button
                    className="button button-primary"
                    disabled={
                      busy ||
                      selected.status !== "REVIEWED" ||
                      dirty ||
                      !!selected.issues?.length ||
                      !acknowledge
                    }
                    onClick={publish}
                  >
                    Publish timetable <ArrowUpRight size={17} />
                  </button>
                </div>
              </section>
            </>
          )}
        </>
      ) : (
        <div className="admin-grid">
          <section className="upload-sheet">
            <Upload size={26} />
            <h2>Upload a timetable</h2>
            <p>
              Keep the old version as history. Publish its replacement from the
              right effective date.
            </p>
            <a
              className="text-link"
              href="/templates/timetable-template.docx"
              download
            >
              Download Word template
            </a>
            <form className="stack-form" onSubmit={upload}>
              <label>
                PDF or editable Word timetable
                <input
                  required
                  type="file"
                  accept=".pdf,.docx"
                  onChange={(e) => setFile(e.target.files?.[0] || null)}
                />
              </label>
              <label>
                Replacement scope
                <input
                  required
                  placeholder="e.g. CSE / semester V / all sections"
                  value={scope}
                  onChange={(e) => setScope(e.target.value)}
                />
              </label>
              <div className="form-columns">
                <label>
                  Effective from
                  <input
                    required
                    type="date"
                    value={from}
                    onChange={(e) => setFrom(e.target.value)}
                  />
                </label>
                <label>
                  Valid until
                  <input
                    required
                    min={from}
                    type="date"
                    value={until}
                    onChange={(e) => setUntil(e.target.value)}
                  />
                </label>
              </div>
              <button disabled={busy} className="button button-primary">
                {busy ? "Reading file…" : "Upload draft"}
                <ArrowUpRight size={17} />
              </button>
            </form>
          </section>
          <section className="version-list">
            <h2>Timetable register</h2>
            {loading ? (
              <Loading />
            ) : versions.length ? (
              versions.map((version) => (
                <button
                  key={version.id}
                  className="version-row"
                  onClick={() => open(version.id)}
                  disabled={busy}
                >
                  <FileText size={24} />
                  <span>
                    <strong>{version.filename}</strong>
                    <small>{version.scope_key}</small>
                    <small>
                      {version.valid_from} to {version.valid_until}
                    </small>
                  </span>
                  <Status value={version.status} />
                </button>
              ))
            ) : (
              <Empty title="No timetables uploaded.">
                Upload the first source file to begin a review.
              </Empty>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
