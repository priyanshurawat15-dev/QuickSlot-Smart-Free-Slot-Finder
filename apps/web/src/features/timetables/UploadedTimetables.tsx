import { useState } from "react";
import { ArrowUpRight, FileText, Trash2 } from "lucide-react";
import type { ImportVersion } from "../../lib/types";
import { Status } from "../../components/ui";
import { timetableValidity } from "./PublicationSchedule";

export function UploadedTimetables({
  versions,
  busy,
  onOpen,
  onDelete,
}: {
  versions: ImportVersion[];
  busy: boolean;
  onOpen(id: string): void;
  onDelete?(version: ImportVersion): Promise<boolean>;
}) {
  const [confirming, setConfirming] = useState<string | null>(null);
  return (
    <div className="timetable-file-grid">
      {versions.map((version) => (
        <article className="timetable-file-card" key={version.id}>
          <div className="timetable-file-top">
            <FileText size={22} />
            <Status value={version.archived_at ? "Deleted" : version.status} />
          </div>
          <button
            className="timetable-file-open"
            onClick={() => onOpen(version.id)}
            disabled={busy}
          >
            <strong>{version.filename}</strong>
            <span>{version.scope_key}</span>
            <small>
              {version.status === "PUBLISHED"
                ? version.publication_windows
                    .map((window) =>
                      timetableValidity(window.valid_from, window.valid_until),
                    )
                    .join("; ") || "No scheduled dates"
                : timetableValidity(version.valid_from, version.valid_until)}
            </small>
            <span className="timetable-open-label">
              View room mappings <ArrowUpRight size={15} />
            </span>
          </button>
          {onDelete &&
            !version.archived_at &&
            (confirming === version.id ? (
              <div className="timetable-delete-confirm">
                <p>
                  Delete this timetable? Its active classes and room mappings
                  will be removed. Earlier dates and the original file stay in
                  history.
                </p>
                <div className="form-actions">
                  <button
                    className="button button-outline button-small"
                    disabled={busy}
                    onClick={() => setConfirming(null)}
                  >
                    Keep timetable
                  </button>
                  <button
                    className="button button-primary button-small"
                    disabled={busy}
                    onClick={async () => {
                      if (await onDelete(version)) setConfirming(null);
                    }}
                  >
                    Delete timetable
                  </button>
                </div>
              </div>
            ) : (
              <button
                className="text-link timetable-delete"
                disabled={busy}
                onClick={() => setConfirming(version.id)}
                aria-label={`Delete ${version.filename}`}
              >
                <Trash2 size={15} />
                Delete
              </button>
            ))}
        </article>
      ))}
    </div>
  );
}
