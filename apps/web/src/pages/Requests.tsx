import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowUpRight,
  ChevronDown,
  ChevronUp,
  Clock3,
  Send,
} from "lucide-react";
import { api } from "../lib/api";
import type { BookingRequest } from "../lib/types";
import { displayDate, time } from "../lib/format";
import { useAuth } from "../lib/auth";
import { SelectField } from "../components/SelectField";
import {
  Empty,
  ErrorText,
  Loading,
  Notice,
  PageHeading,
  Status,
} from "../components/ui";
export function Requests({ review = false }: { review?: boolean }) {
  const { user } = useAuth();
  const [params] = useSearchParams();
  const [requests, setRequests] = useState<BookingRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [filter, setFilter] = useState("");
  const [expanded, setExpanded] = useState<string | null>(null);
  const [decision, setDecision] = useState<{
    id: string;
    action: string;
  } | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState("");
  useEffect(() => {
    api
      .requests()
      .then(setRequests)
      .catch(setError)
      .finally(() => setLoading(false));
  }, []);
  const act = async () => {
    if (!decision) return;
    if (
      ["reject", "forward", "cancel"].includes(decision.action) &&
      !reason.trim()
    ) {
      setError(
        new Error("Add a reason so everyone understands this decision."),
      );
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await api.decide(decision.id, decision.action, reason);
      setRequests((values) =>
        values.map((value) => (value.id === updated.id ? updated : value)),
      );
      setSuccess(`Request updated: ${updated.status.toLowerCase()}.`);
      setDecision(null);
      setReason("");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  const shown = requests.filter(
    (request) => !filter || request.status === filter,
  );
  return (
    <div className="container page">
      <PageHeading
        title={review ? "Review requests." : "My requests."}
        text={
          review
            ? user?.role === "HOD"
              ? "Review room requests and decisions, including requests forwarded by the coordinator."
              : "Review requests for rooms in your allocation scope. Forward to the HOD when a decision needs their permission."
            : "View request status and decisions."
        }
        action={
          !review && (
            <Link className="button button-primary" to="/requests/new">
              New request <ArrowUpRight size={18} />
            </Link>
          )
        }
      />
      {params.has("created") && (
        <Notice tone="success">
          Your request has been sent to the allocation faculty. It is awaiting a
          decision.
        </Notice>
      )}
      {success && <Notice tone="success">{success}</Notice>}
      {!!error && <ErrorText error={error} />}
      <div className="request-toolbar">
        <span>
          {requests.length} {review ? "requests in your scope" : "requests"}
        </span>
        <label>
          Show
          <SelectField
            aria-label="Show"
            value={filter}
            onValueChange={setFilter}
            options={[
              { value: "", label: "All requests" },
              ...[
                "PENDING",
                "FORWARDED",
                "APPROVED",
                "REJECTED",
                "WITHDRAWN",
                "CANCELLED",
              ].map((status) => ({
                value: status,
                label:
                  status[0] +
                  status.slice(1).toLowerCase().replaceAll("_", " "),
              })),
            ]}
          />
        </label>
      </div>
      {loading ? (
        <Loading />
      ) : !shown.length ? (
        <Empty title="No requests found.">
          {review
            ? "Requests will appear here when someone needs a room."
            : "Choose a room and time to submit a request."}
        </Empty>
      ) : (
        <div className="request-list">
          {shown.map((request) => {
            const isOpen = expanded === request.id;
            const pending = ["PENDING", "FORWARDED"].includes(request.status);
            const canDecide =
              review &&
              pending &&
              (user?.role === "HOD" || request.status === "PENDING");
            return (
              <article className="request-card" key={request.id}>
                <div className="request-card-top">
                  <div>
                    <div className="request-meta">
                      <span>Room {request.room_code}</span>
                      <span>{displayDate(request.date)}</span>
                      <span>
                        <Clock3 size={14} />
                        {time(request.start_time)}–{time(request.end_time)}
                      </span>
                    </div>
                    <h2>{request.purpose}</h2>
                    <p>
                      {request.requester_name} · {request.attendance} people
                    </p>
                    <p>
                      {request.reason_category
                        ?.toLowerCase()
                        .replaceAll("_", " ")}
                      {request.club_name ? ` · ${request.club_name}` : ""}
                    </p>
                  </div>
                  <Status value={request.status} />
                </div>
                <div className="request-card-bottom">
                  <button
                    className="text-link"
                    aria-expanded={isOpen}
                    onClick={() => setExpanded(isOpen ? null : request.id)}
                  >
                    {isOpen ? "Hide history" : "View history"}
                    {isOpen ? (
                      <ChevronUp size={16} />
                    ) : (
                      <ChevronDown size={16} />
                    )}
                  </button>
                  <div className="decision-actions">
                    {canDecide && (
                      <>
                        <button
                          className="button button-outline button-small"
                          onClick={() => {
                            setDecision({ id: request.id, action: "reject" });
                            setReason("");
                          }}
                        >
                          Reject
                        </button>
                        {user?.role !== "HOD" && (
                          <button
                            className="button button-outline button-small"
                            onClick={() => {
                              setDecision({
                                id: request.id,
                                action: "forward",
                              });
                              setReason("");
                            }}
                          >
                            <Send size={14} />
                            Forward to HOD
                          </button>
                        )}
                        <button
                          className="button button-dark button-small"
                          onClick={() => {
                            setDecision({ id: request.id, action: "approve" });
                            setReason("");
                          }}
                        >
                          Approve
                        </button>
                      </>
                    )}
                    {!review && pending && (
                      <button
                        className="button button-outline button-small"
                        onClick={() => {
                          setDecision({ id: request.id, action: "withdraw" });
                          setReason("");
                        }}
                      >
                        Withdraw
                      </button>
                    )}
                    {!review && request.status === "APPROVED" && (
                      <button
                        className="button button-outline button-small"
                        onClick={() => {
                          setDecision({ id: request.id, action: "cancel" });
                          setReason("");
                        }}
                      >
                        Cancel reservation
                      </button>
                    )}
                  </div>
                </div>
                {isOpen && (
                  <ol className="history-list">
                    {request.history.length ? (
                      request.history.map((event, i) => (
                        <li key={i}>
                          <strong>
                            {(event.action || event.status || "Updated")
                              .replaceAll("_", " ")
                              .toLowerCase()}
                          </strong>
                          <span>
                            {event.actor_name}
                            {event.created_at &&
                              ` · ${new Date(event.created_at).toLocaleString("en-IN", { timeZone: "Asia/Kolkata" })}`}
                          </span>
                          {event.reason && <p>{event.reason}</p>}
                        </li>
                      ))
                    ) : (
                      <li>No additional history recorded.</li>
                    )}
                  </ol>
                )}
                {decision?.id === request.id && (
                  <section
                    className="decision-panel"
                    aria-label="Confirm decision"
                  >
                    <h3>
                      {decision.action === "forward"
                        ? "Forward this request to the HOD"
                        : `${decision.action[0].toUpperCase()}${decision.action.slice(1)} this request`}
                    </h3>
                    <p>
                      {decision.action === "approve"
                        ? "Availability and booking conflicts will be checked again before approval."
                        : "This decision and your reason will appear in the request history."}
                    </p>
                    <label>
                      Reason{" "}
                      {decision.action === "approve" ||
                      decision.action === "withdraw"
                        ? "(optional)"
                        : "(required)"}
                      <textarea
                        value={reason}
                        rows={3}
                        onChange={(e) => setReason(e.target.value)}
                        autoFocus
                      />
                    </label>
                    <div className="decision-actions">
                      <button
                        className="button button-outline button-small"
                        onClick={() => setDecision(null)}
                        disabled={busy}
                      >
                        Keep reviewing
                      </button>
                      <button
                        className="button button-primary button-small"
                        onClick={act}
                        disabled={busy}
                      >
                        {busy ? "Saving…" : "Confirm decision"}
                      </button>
                    </div>
                  </section>
                )}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
