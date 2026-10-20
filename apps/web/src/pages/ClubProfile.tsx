import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Check,
  Instagram,
  Linkedin,
  Mail,
  ShieldCheck,
} from "lucide-react";
import { useAuth } from "../lib/auth";
import {
  clubs,
  type Club,
  type ClubMembership,
  type MemberApplication,
} from "../lib/clubs";
import { Empty, ErrorText, Loading, Notice } from "../components/ui";
import { ClubLogo } from "../components/ClubLogo";
import "../styles/clubs.css";

const membershipLabels = {
  NONE: "Not a member",
  PENDING: "Awaiting verification",
  VERIFIED: "Verified member",
  REJECTED: "Application declined",
};

export function ClubProfile() {
  const { slug = "" } = useParams();
  const { user, loading: authLoading } = useAuth();
  const [club, setClub] = useState<Club>();
  const [membership, setMembership] = useState<ClubMembership>();
  const [error, setError] = useState<unknown>();
  const [membershipError, setMembershipError] = useState<unknown>();
  const [joining, setJoining] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    clubs
      .profile(slug, controller.signal)
      .then(setClub)
      .catch((failure) => {
        if (!controller.signal.aborted) setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [slug]);

  useEffect(() => {
    let current = true;
    setMembership(undefined);
    setMembershipError(undefined);
    if (user)
      clubs
        .membership(slug)
        .then((value) => {
          if (current) setMembership(value);
        })
        .catch((failure) => {
          if (current) setMembershipError(failure);
        });
    return () => {
      current = false;
    };
  }, [slug, user]);

  async function join() {
    setJoining(true);
    setMembershipError(undefined);
    try {
      await clubs.join(slug);
      setMembership(await clubs.membership(slug));
    } catch (failure) {
      setMembershipError(failure);
    } finally {
      setJoining(false);
    }
  }

  if (loading)
    return (
      <div className="container page">
        <Loading label="Loading club profile…" />
      </div>
    );
  if (error)
    return (
      <div className="container page">
        <ErrorText error={error} />
      </div>
    );
  if (!club) return null;

  const facultyCoordinators = club.leadership.filter((leader) =>
    /\bfaculty\b/i.test(leader.role),
  );
  const coreTeam = club.leadership.filter(
    (leader) => !/\bfaculty\b/i.test(leader.role),
  );

  return (
    <div className="container page clubs-page">
      <Link className="club-back-link" to="/clubs">
        <ArrowLeft size={16} />
        All clubs
      </Link>
      <section className="club-profile-heading">
        <ClubLogo
          name={club.name}
          src={club.slug === "codev" ? "/brand/codev-logo.png" : undefined}
          large
        />
        <div>
          <p className="club-category">Student club</p>
          <h1>{club.name}</h1>
          <p>{club.summary}</p>
        </div>
      </section>
      <div className="club-profile-grid">
        <div className="club-profile-main">
          <section className="club-section">
            <h2>About the club</h2>
            <p className="club-description">{club.description}</p>
            <div className="club-contact-links">
              {club.email && (
                <a href={`mailto:${club.email}`}>
                  <Mail size={17} />
                  {club.email}
                </a>
              )}
              {club.instagram && (
                <a href={club.instagram} target="_blank" rel="noreferrer">
                  <Instagram size={17} />
                  Instagram
                </a>
              )}
              {club.linkedin && (
                <a href={club.linkedin} target="_blank" rel="noreferrer">
                  <Linkedin size={17} />
                  LinkedIn
                </a>
              )}
            </div>
          </section>
          {facultyCoordinators.length > 0 && (
            <section
              className="club-section club-faculty-section"
              aria-labelledby="faculty-coordinator-heading"
            >
              <h2 id="faculty-coordinator-heading">Faculty coordinator</h2>
              {facultyCoordinators.map((leader) => (
                <div
                  className="club-faculty-member"
                  key={`${leader.name}-${leader.role}`}
                >
                  <h3>{leader.name}</h3>
                  <p>{leader.role}</p>
                </div>
              ))}
            </section>
          )}
          {coreTeam.length > 0 && (
            <section
              className="club-section"
              aria-labelledby="core-team-heading"
            >
              <h2 id="core-team-heading">Core team</h2>
              <div className="club-leadership">
                {coreTeam.map((leader) => (
                  <div
                    className="club-leader"
                    key={`${leader.name}-${leader.role}`}
                  >
                    <div>
                      <h3>{leader.name}</h3>
                      <p>{leader.role}</p>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}
          {membership?.can_review && <MembershipReview slug={slug} />}
        </div>
        <aside className="club-membership-panel">
          <ShieldCheck size={26} aria-hidden="true" />
          <h2>Club membership</h2>
          {!!membershipError && <ErrorText error={membershipError} />}
          {authLoading || (user && !membership && !membershipError) ? (
            <Loading label="Checking membership…" />
          ) : !user ? (
            <>
              <p>
                Sign in with your student account to apply. A club reviewer will
                verify your membership.
              </p>
              <Link
                className="club-button"
                to={`/login?returnTo=${encodeURIComponent(`/clubs/${slug}`)}`}
              >
                Sign in to join
              </Link>
            </>
          ) : membership?.can_review ? (
            <>
              <span className="membership-badge membership-verified">
                <Check size={15} />
                Club reviewer
              </span>
              <p>You can verify or decline student applications below.</p>
            </>
          ) : user.role !== "STUDENT" ? (
            <p>Student accounts can apply for club membership here.</p>
          ) : membership ? (
            <>
              <span
                className={`membership-badge membership-${membership.status.toLowerCase()}`}
              >
                {membershipLabels[membership.status]}
              </span>
              <p>
                {membership.status === "VERIFIED"
                  ? "Your membership has been verified by the club."
                  : membership.status === "PENDING"
                    ? "Your application has been sent to the club. Check back here for the decision."
                    : membership.status === "REJECTED"
                      ? "Contact the club to discuss the decision, or send a new application when your details are ready."
                      : "Apply to join. A club reviewer will confirm your membership."}
              </p>
              {["NONE", "REJECTED"].includes(membership.status) && (
                <button
                  className="club-button"
                  disabled={joining}
                  onClick={join}
                >
                  {joining
                    ? "Sending application…"
                    : membership.status === "REJECTED"
                      ? "Apply again"
                      : "Apply to join"}
                </button>
              )}
            </>
          ) : null}
          <p className="club-membership-note">
            Verified members can request a room for their club. The allocation
            coordinator must approve the request before it becomes a booking.
          </p>
        </aside>
      </div>
    </div>
  );
}

function MembershipReview({ slug }: { slug: string }) {
  const [applications, setApplications] = useState<MemberApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [decision, setDecision] = useState<{
    id: string;
    action: "approve" | "reject";
  }>();
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let current = true;
    clubs
      .applications(slug)
      .then((value) => {
        if (current) setApplications(value);
      })
      .catch((failure) => {
        if (current) setError(failure);
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [slug]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!decision) return;
    setSaving(true);
    setError(undefined);
    try {
      await clubs.decide(slug, decision.id, decision.action, reason.trim());
      setApplications(await clubs.applications(slug));
      setMessage(
        decision.action === "approve"
          ? "Membership verified."
          : "Application declined.",
      );
      setDecision(undefined);
      setReason("");
    } catch (failure) {
      setError(failure);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="club-section club-review-section">
      <h2>Membership applications</h2>
      {!!error && <ErrorText error={error} />}
      {message && <Notice tone="success">{message}</Notice>}
      {loading ? (
        <Loading label="Loading applications…" />
      ) : !applications.length ? (
        <Empty title="No applications yet">
          Student membership applications will appear here.
        </Empty>
      ) : (
        <div className="club-applications">
          {applications.map((application) => (
            <article className="club-application" key={application.id}>
              <div>
                <h3>{application.student_name}</h3>
                <p>
                  Applied{" "}
                  {new Intl.DateTimeFormat("en-IN", {
                    day: "numeric",
                    month: "short",
                    year: "numeric",
                    timeZone: "Asia/Kolkata",
                  }).format(new Date(application.created_at))}
                </p>
              </div>
              <span
                className={`membership-badge membership-${application.status.toLowerCase()}`}
              >
                {membershipLabels[application.status]}
              </span>
              {application.status === "PENDING" && (
                <div className="club-review-actions">
                  <button
                    type="button"
                    className="club-verify-button"
                    onClick={() => {
                      setDecision({ id: application.id, action: "approve" });
                      setReason("");
                      setMessage("");
                    }}
                  >
                    Verify
                  </button>
                  <button
                    type="button"
                    className="button button-outline"
                    onClick={() => {
                      setDecision({ id: application.id, action: "reject" });
                      setReason("");
                      setMessage("");
                    }}
                  >
                    Decline
                  </button>
                </div>
              )}
              {decision?.id === application.id && (
                <form className="club-decision-form" onSubmit={submit}>
                  <label>
                    {decision.action === "reject"
                      ? "Reason for declining"
                      : "Verification note"}
                    <textarea
                      required={decision.action === "reject"}
                      maxLength={500}
                      value={reason}
                      onChange={(event) => setReason(event.target.value)}
                      rows={2}
                    />
                  </label>
                  <div>
                    <button
                      type="submit"
                      className="club-button"
                      disabled={saving}
                    >
                      {saving
                        ? "Saving…"
                        : decision.action === "approve"
                          ? "Confirm verification"
                          : "Confirm decline"}
                    </button>
                    <button
                      type="button"
                      className="button button-outline"
                      disabled={saving}
                      onClick={() => setDecision(undefined)}
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
