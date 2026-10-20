import { useMemo, useState } from "react";
import type { ImportIssue, ImportSession } from "../../lib/types";
import "./issue-summary.css";

interface IssueGroup {
  code: string;
  message: string;
  issues: ImportIssue[];
}

function GroupReferences({
  group,
  rows,
}: {
  group: IssueGroup;
  rows: Map<string, number>;
}) {
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  const start = page * pageSize;
  const label = group.code.toLowerCase().replaceAll("_", " ");
  const title = label.charAt(0).toUpperCase() + label.slice(1);

  return (
    <details
      className="issue-group"
      onToggle={(event) => setOpen(event.currentTarget.open)}
    >
      <summary>
        <strong>
          {title} · {group.issues.length}
        </strong>
        <span>{group.message}</span>
      </summary>
      {open && (
        <>
          <ul className="issue-references">
            {group.issues
              .slice(start, start + pageSize)
              .map((issue, offset) => {
                const row = issue.session_id
                  ? rows.get(issue.session_id)
                  : null;
                const sourcePage = issue.source?.page;
                const reference = [
                  row ? `Review row ${row}` : "",
                  sourcePage ? `Source page ${String(sourcePage)}` : "",
                ]
                  .filter(Boolean)
                  .join(" · ");
                return (
                  <li key={start + offset}>{reference || "Whole document"}</li>
                );
              })}
          </ul>
          {group.issues.length > pageSize && (
            <nav className="form-actions" aria-label={`${title} references`}>
              <button
                type="button"
                className="button button-outline button-small"
                disabled={page === 0}
                onClick={() => setPage((current) => current - 1)}
              >
                Previous references
              </button>
              <span>
                {start + 1}–{Math.min(start + pageSize, group.issues.length)} of{" "}
                {group.issues.length}
              </span>
              <button
                type="button"
                className="button button-outline button-small"
                disabled={start + pageSize >= group.issues.length}
                onClick={() => setPage((current) => current + 1)}
              >
                Next references
              </button>
            </nav>
          )}
        </>
      )}
    </details>
  );
}

export function IssueSummary({
  issues,
  sessions,
}: {
  issues: ImportIssue[];
  sessions: ImportSession[];
}) {
  const groups = useMemo(() => {
    const grouped = new Map<string, IssueGroup>();
    for (const issue of issues) {
      const key = JSON.stringify([issue.code, issue.message]);
      const group = grouped.get(key);
      if (group) group.issues.push(issue);
      else
        grouped.set(key, {
          code: issue.code,
          message: issue.message,
          issues: [issue],
        });
    }
    return [...grouped.values()];
  }, [issues]);
  const rows = useMemo(
    () =>
      new Map(
        sessions.flatMap((session, index) =>
          session.id ? [[session.id, index + 1] as const] : [],
        ),
      ),
    [sessions],
  );

  return (
    <section className="issue-list" aria-label="Timetable review issues">
      <h3>Resolve before publication</h3>
      <p>
        {issues.length} review items · {groups.length} groups
      </p>
      {groups.map((group) => (
        <GroupReferences
          key={JSON.stringify([group.code, group.message])}
          group={group}
          rows={rows}
        />
      ))}
    </section>
  );
}
