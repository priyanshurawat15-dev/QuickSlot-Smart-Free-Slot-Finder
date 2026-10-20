import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, Mail, Users } from "lucide-react";
import { ClubLogo } from "../components/ClubLogo";
import { clubs, type Club } from "../lib/clubs";
import { Empty, ErrorText, Loading, PageHeading } from "../components/ui";
import "../styles/clubs.css";

export function Clubs() {
  const [directory, setDirectory] = useState<Club[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();

  useEffect(() => {
    const controller = new AbortController();
    clubs
      .list(controller.signal)
      .then(setDirectory)
      .catch((failure) => {
        if (!controller.signal.aborted) setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  return (
    <div className="container page clubs-page">
      <PageHeading
        title="Clubs"
        text="Meet the clubs and apply for verified membership."
      />
      {loading ? (
        <Loading label="Loading clubs…" />
      ) : error ? (
        <ErrorText error={error} />
      ) : directory.length ? (
        <div className="club-directory">
          <div className="club-directory-list">
            {directory.map((club) => (
              <article className="club-directory-card" key={club.id}>
                <div className="club-directory-identity">
                  <ClubLogo
                    name={club.name}
                    src={
                      club.slug === "codev"
                        ? "/brand/codev-logo.png"
                        : undefined
                    }
                  />
                  <div>
                    <p className="club-category">Student club</p>
                    <h2>
                      <Link to={`/clubs/${club.slug}`}>{club.name}</Link>
                    </h2>
                  </div>
                </div>
                <p className="club-summary">{club.summary}</p>
                <div className="club-card-contact">
                  <Mail size={16} />
                  <a href={`mailto:${club.email}`}>{club.email}</a>
                </div>
                <div className="club-card-bottom">
                  <span>
                    <Users size={16} />
                    Membership by verification
                  </span>
                  <Link
                    className="club-profile-link"
                    to={`/clubs/${club.slug}`}
                  >
                    View club
                    <ArrowUpRight size={17} />
                  </Link>
                </div>
              </article>
            ))}
          </div>
          <aside className="club-join-guide">
            <h2>How membership works</h2>
            <ol>
              <li>
                <strong>Apply as a student</strong>
                <p>Sign in, open the club profile and send your application.</p>
              </li>
              <li>
                <strong>The club head verifies</strong>
                <p>
                  The club head checks your application and records a decision.
                </p>
              </li>
              <li>
                <strong>Check your membership</strong>
                <p>Your verified status appears on the club profile.</p>
              </li>
            </ol>
          </aside>
        </div>
      ) : (
        <Empty title="No clubs listed yet">
          Club profiles will appear here when added by an administrator.
        </Empty>
      )}
    </div>
  );
}
