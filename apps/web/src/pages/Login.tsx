import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { ArrowLeft, ArrowUpRight, KeyRound } from "lucide-react";
import { useAuth } from "../lib/auth";
import { ErrorText } from "../components/ui";
const demoAccounts = [
  { label: "Student", name: "student" },
  { label: "Faculty", name: "faculty" },
  { label: "Club head", name: "club" },
  { label: "Coordinator", name: "coordinator" },
  { label: "HOD", name: "hod" },
  { label: "Administrator", name: "admin" },
];
export function Login() {
  const { login, user } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const destination = params.get("returnTo");
  const returnTo =
    destination?.startsWith("/") && !destination.startsWith("//")
      ? destination
      : "/";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  if (user) return <Navigate to={returnTo} replace />;
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate(returnTo, { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="container page login-layout">
      <div className="login-intro">
        <Link to="/" className="back-link">
          <ArrowLeft size={16} /> Back to room finder
        </Link>
        <h1>Sign in.</h1>
        <p>
          Sign in to manage club membership or room requests. Verified club
          members can request rooms for their club.
        </p>
        <div className="login-note">
          Public room search does not require sign in.
        </div>
      </div>
      <div className="login-sheet">
        <KeyRound size={25} />
        <h2>Account access</h2>
        <p>Use your approved account to continue.</p>
        <form onSubmit={submit} className="stack-form">
          <label>
            Email address
            <input
              type="email"
              required
              autoComplete="username"
              value={email}
              placeholder="you@example.edu"
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          {!!error && <ErrorText error={error} />}
          <button className="button button-primary" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
            <ArrowUpRight size={18} />
          </button>
        </form>
        {import.meta.env.DEV && (
          <details className="demo-accounts">
            <summary>Test accounts</summary>
            <p>Select an account to fill its credentials.</p>
            <div className="demo-buttons">
              {demoAccounts.map((account) => (
                <button
                  type="button"
                  key={account.name}
                  onClick={() => {
                    setEmail(`${account.name}@demo.quickslot.test`);
                    setPassword("QuickSlot-demo-2026!");
                  }}
                >
                  {account.label}
                </button>
              ))}
            </div>
            <small>
              Password: <code>QuickSlot-demo-2026!</code>
            </small>
          </details>
        )}
      </div>
    </div>
  );
}
