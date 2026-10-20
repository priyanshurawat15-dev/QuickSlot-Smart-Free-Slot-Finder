import { useEffect, useRef, useState } from "react";
import { ArrowUpRight, Check, ChevronLeft } from "lucide-react";
import { TimetableUpload } from "../features/timetables/TimetableUpload";
import { UploadedTimetables } from "../features/timetables/UploadedTimetables";
import { RoomMappingReview } from "../features/timetables/RoomMappingReview";
import {
  PublicationSchedule,
  timetableValidity,
} from "../features/timetables/PublicationSchedule";
import { SessionReviewTable } from "../features/timetables/SessionReviewTable";
import { IssueSummary } from "../features/timetables/IssueSummary";
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
import "../features/timetables/timetables.css";

function isCurrent(version: ImportVersion) {
  return (
    !version.archived_at &&
    (version.status !== "PUBLISHED" ||
      version.publication_windows.some(
        (window) => !window.valid_until || window.valid_until >= campusToday(),
      ))
  );
}

export function Timetables() {
  const [faculty, setFaculty] = useState<{ id: string; name: string }[]>([]);
  const [versions, setVersions] = useState<ImportVersion[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [selected, setSelected] = useState<ImportVersion | null>(null);
  const [sessions, setSessions] = useState<ImportSession[]>([]);
  const [coverage, setCoverage] = useState<string[]>([]);
  const [acknowledge, setAcknowledge] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [reviewRow, setReviewRow] = useState(0);
  const [focusSequence, setFocusSequence] = useState(0);
  const advanced = useRef<HTMLDetailsElement>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState("");
  const current = versions.filter(isCurrent);
  const history = versions.filter((version) => !isCurrent(version));
  const editable =
    !!selected && !selected.archived_at && selected.status !== "PUBLISHED";

  const load = async () => {
    const [list, inventory, people] = await Promise.all([
      api.versions(true),
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

  const select = (detail: ImportVersion) => {
    setSelected(detail);
    setSessions(detail.sessions || []);
    setCoverage(detail.coverage_room_ids || []);
    setAcknowledge(false);
    setDirty(false);
    setAdvancedOpen(false);
    setReviewRow(0);
    setFocusSequence(0);
  };
  const open = async (id: string) => {
    setBusy(true);
    setError(null);
    setSuccess("");
    try {
      select(await api.version(id));
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };
  const upload = async (
    file: File,
    scope: string,
    from: string,
    until: string | null,
  ) => {
    setBusy(true);
    setError(null);
    setSuccess("");
    try {
      select(await api.upload(file, scope, from, until));
      setSuccess(
        "Timetable uploaded. Check the matched rooms and flagged rows.",
      );
      await load();
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };
  const deleteTimetable = async (version: ImportVersion) => {
    setBusy(true);
    setError(null);
    setSuccess("");
    try {
      await api.deleteTimetable(version.id, version.version);
      setSuccess(
        "Timetable deleted. Its active classes and room mappings have been removed.",
      );
      await load();
      return true;
    } catch (failure) {
      setError(failure);
      return false;
    } finally {
      setBusy(false);
    }
  };
  const review = async () => {
    if (!selected) return;
    setBusy(true);
    setError(null);
    setSuccess("");
    try {
      const result = await api.review(
        selected.id,
        sessions,
        coverage,
        acknowledge,
        selected.version,
      );
      setSelected(result);
      setSessions(result.sessions || []);
      setDirty(false);
      setSuccess("Review saved. The timetable is ready to publish.");
      await load();
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };
  const publish = async () => {
    if (!selected) return;
    setBusy(true);
    setError(null);
    setSuccess("");
    try {
      setSelected(await api.publish(selected.id, selected.version));
      setDirty(false);
      setSuccess(
        "Timetable published. Room availability now includes these classes.",
      );
      await load();
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };
  const retire = async (removeFrom: string) => {
    if (!selected) return false;
    setBusy(true);
    setError(null);
    setSuccess("");
    try {
      setSelected(await api.retire(selected.id, selected.version, removeFrom));
      setSuccess(
        `Timetable removed from ${removeFrom} onward. Earlier dates remain in history.`,
      );
      await load();
      return true;
    } catch (failure) {
      setError(failure);
      return false;
    } finally {
      setBusy(false);
    }
  };
  const changed = () => {
    setAcknowledge(false);
    setDirty(true);
  };
  const update = (index: number, patch: Partial<ImportSession>) => {
    setSessions((current) =>
      current.map((session, position) =>
        position === index ? { ...session, ...patch } : session,
      ),
    );
    changed();
  };
  const reviewMapping = (index: number) => {
    setReviewRow(index);
    setFocusSequence((sequence) => sequence + 1);
    setAdvancedOpen(true);
    requestAnimationFrame(() =>
      advanced.current?.scrollIntoView({ block: "start" }),
    );
  };

  return (
    <div className="container page timetable-page">
      <PageHeading
        title="Timetables."
        text="Upload class schedules and check their room mappings."
      />
      {!!error && <ErrorText error={error} />}
      {success && <Notice tone="success">{success}</Notice>}
      {selected ? (
        <>
          <button
            className="back-link"
            disabled={busy}
            onClick={() => {
              setSelected(null);
              setSuccess("");
              setError(null);
            }}
          >
            <ChevronLeft size={16} />
            All timetables
          </button>
          <div className="version-heading">
            <div>
              <h2>{selected.filename}</h2>
              <p>
                {selected.scope_key} ·{" "}
                {timetableValidity(selected.valid_from, selected.valid_until)}
              </p>
            </div>
            <Status
              value={
                selected.archived_at
                  ? "Deleted"
                  : isCurrent(selected)
                    ? selected.status
                    : "Historical"
              }
            />
          </div>
          {selected.archived_at && (
            <Notice>
              This timetable is in history. It no longer supplies active classes
              or room mappings.
            </Notice>
          )}
          <RoomMappingReview
            key={`mapping-${selected.id}`}
            sessions={sessions}
            rooms={rooms}
            busy={busy}
            onReview={reviewMapping}
          />
          {editable && (
            <section className="mapping-confirmation">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  disabled={busy}
                  checked={acknowledge}
                  onChange={(event) => setAcknowledge(event.target.checked)}
                />
                I checked the source timetable, room mappings and class timings.
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
                  Publish timetable
                  <ArrowUpRight size={17} />
                </button>
              </div>
            </section>
          )}
          <details
            className="timetable-disclosure"
            ref={advanced}
            open={advancedOpen}
            onToggle={(event) => setAdvancedOpen(event.currentTarget.open)}
          >
            <summary>Advanced session editor</summary>
            <div className="timetable-disclosure-content">
              <a
                className="text-link source-download"
                href={`/api/timetables/${selected.id}/source`}
                target="_blank"
                rel="noreferrer"
              >
                Download original source
                <ArrowUpRight size={15} />
              </a>
              <FacultyDirectory
                faculty={faculty}
                disabled={busy || !editable}
                onCreated={(person) =>
                  setFaculty((current) => [...current, person])
                }
              />
              <SessionReviewTable
                key={`sessions-${selected.id}-${focusSequence}`}
                initialRow={reviewRow}
                sessions={sessions}
                rooms={rooms}
                faculty={faculty}
                editable={editable && !busy}
                update={update}
                onRemove={(index) => {
                  setSessions((current) =>
                    current.filter((_, position) => position !== index),
                  );
                  changed();
                }}
              />
              {editable && (
                <>
                  <button
                    className="button button-outline button-small"
                    disabled={busy}
                    onClick={() => {
                      setSessions((current) => [
                        ...current,
                        {
                          weekday: 0,
                          start_time: "09:00",
                          end_time: "10:00",
                          room_id: null,
                          subject: "",
                          faculty_ids: [],
                        },
                      ]);
                      changed();
                    }}
                  >
                    Add session
                  </button>
                  <section className="coverage-review">
                    <h3>Rooms with complete schedule coverage</h3>
                    <p>
                      Select a room only when all courses using it are covered
                      by verified schedules. A room appearing in this timetable
                      is not enough.
                    </p>
                    <div className="coverage-checks">
                      {rooms.map((room) => (
                        <label key={room.id}>
                          <input
                            type="checkbox"
                            disabled={busy}
                            checked={coverage.includes(room.id)}
                            onChange={(event) => {
                              setCoverage((current) =>
                                event.target.checked
                                  ? [...current, room.id]
                                  : current.filter((id) => id !== room.id),
                              );
                              changed();
                            }}
                          />
                          {room.code}
                          {room.building && <small>{room.building}</small>}
                        </label>
                      ))}
                    </div>
                  </section>
                </>
              )}
              {selected.status === "PUBLISHED" && !selected.archived_at && (
                <PublicationSchedule
                  key={`publication-${selected.id}`}
                  version={selected}
                  busy={busy}
                  onRetire={retire}
                />
              )}
            </div>
          </details>
          <details className="timetable-disclosure">
            <summary>
              Import logs · {selected.issues?.length || 0} review items
            </summary>
            <div className="timetable-disclosure-content">
              {selected.issues?.length ? (
                <IssueSummary
                  key={`issues-${selected.id}`}
                  issues={selected.issues}
                  sessions={sessions}
                />
              ) : (
                <p>No unresolved import issues.</p>
              )}
            </div>
          </details>
        </>
      ) : (
        <>
          <TimetableUpload busy={busy} onUpload={upload} />
          <section className="timetable-current">
            <div className="timetable-section-heading">
              <h2>Uploaded timetables</h2>
              <span>{current.length} files</span>
            </div>
            {loading ? (
              <Loading />
            ) : current.length ? (
              <UploadedTimetables
                versions={current}
                busy={busy}
                onOpen={open}
                onDelete={deleteTimetable}
              />
            ) : (
              <Empty title="No timetables uploaded.">
                Upload a timetable to check its room mappings.
              </Empty>
            )}
          </section>
          <details className="timetable-disclosure">
            <summary>Logs and history · {history.length} files</summary>
            <div className="timetable-disclosure-content">
              {history.length ? (
                <UploadedTimetables
                  versions={history}
                  busy={busy}
                  onOpen={open}
                />
              ) : (
                <p>No previous timetables.</p>
              )}
            </div>
          </details>
        </>
      )}
    </div>
  );
}
