import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { ArrowUpRight, LogOut, Menu, X } from "lucide-react";
import { useState } from "react";
import { canRequest, canReview, canViewFaculty, useAuth } from "../lib/auth";
import { roleName } from "../lib/format";

export function Layout() {
  const { user, logout } = useAuth();
  const [menu, setMenu] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const signOut = async () => {
    try {
      await logout();
      navigate("/");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Sign out failed");
    }
  };
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="institution-header">
        <div className="header-shell container">
          <div className="institution-masthead">
            <a
              className="university-wordmark"
              href="https://gehu.ac.in/"
              target="_blank"
              rel="noreferrer"
              aria-label="Graphic Era Hill University website"
            >
              <img src="/brand/gehu-emblem.svg" alt="" width="46" height="46" />
              <span>
                Graphic Era<span>Hill University</span>
              </span>
            </a>
            <button
              className="mobile-toggle icon-button"
              aria-expanded={menu}
              aria-label="Toggle navigation"
              aria-controls="main-navigation"
              onClick={() => setMenu(!menu)}
            >
              {menu ? <X /> : <Menu />}
            </button>
          </div>
          <div className="navigation-bar">
            <div className="navigation-inner">
              <nav
                id="main-navigation"
                className={menu ? "navigation open" : "navigation"}
                aria-label="Main navigation"
                onClick={() => setMenu(false)}
              >
                <NavLink end to="/">
                  Room availability
                </NavLink>
                {canViewFaculty(user?.role) && (
                  <NavLink to="/teachers">Faculty timetable</NavLink>
                )}
                <NavLink to="/clubs">Clubs</NavLink>
                {canRequest(user?.role) && (
                  <NavLink to="/requests">My requests</NavLink>
                )}
                {canReview(user?.role) && (
                  <NavLink to="/approvals">Review requests</NavLink>
                )}
                {user?.role === "ADMIN" && (
                  <>
                    <NavLink to="/admin/timetables">Timetables</NavLink>
                    <NavLink to="/admin/rooms">Room register</NavLink>
                  </>
                )}
                {user ? (
                  <div className="user-nav">
                    <span>
                      {user.full_name}
                      <small>{roleName(user.role)}</small>
                    </span>
                    <button
                      className="icon-button"
                      onClick={signOut}
                      aria-label="Sign out"
                    >
                      <LogOut size={18} />
                    </button>
                  </div>
                ) : (
                  <Link to="/login" className="staff-login">
                    Sign in <ArrowUpRight size={15} />
                  </Link>
                )}
              </nav>
            </div>
          </div>
        </div>
      </header>
      {error && (
        <div role="alert" className="header-error">
          {error}
        </div>
      )}
      <main id="main" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <div className="footer-brand">
            <Link to="/" className="footer-title">
              QuickSlot
            </Link>
            <p>Campus room schedules and booking requests.</p>
          </div>
          <nav className="footer-links" aria-label="Footer navigation">
            <h2>Explore</h2>
            <Link to="/">Room availability</Link>
            {canViewFaculty(user?.role) && (
              <Link to="/teachers">Faculty timetable</Link>
            )}
            <Link to="/clubs">Club profiles</Link>
            <Link to="/login">Sign in</Link>
          </nav>
          <div className="footer-guidance">
            <h2>Using a room</h2>
            <p>
              Students can check availability. Faculty and verified club heads
              can request a booking.
            </p>
            <Link to="/requests/new">
              Request a room <ArrowUpRight size={14} />
            </Link>
          </div>
          <div className="footer-university">
            <h2>Graphic Era Hill University</h2>
            <a href="https://gehu.ac.in/" target="_blank" rel="noreferrer">
              University website <ArrowUpRight size={14} />
            </a>
          </div>
        </div>
        <div className="container footer-bottom">
          <span>QuickSlot · Campus room availability & allocation</span>
          <span>Check the room in person before use.</span>
        </div>
      </footer>
    </>
  );
}
