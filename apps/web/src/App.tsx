import { RoutePosition } from "./components/RoutePosition";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import {
  AuthProvider,
  canRequest,
  canReview,
  canViewFaculty,
  useAuth,
} from "./lib/auth";
import { Layout } from "./components/Layout";
import { Loading, Notice } from "./components/ui";
import { Finder } from "./pages/Finder";
import { RoomDetail } from "./pages/RoomDetail";
import { Login } from "./pages/Login";
import { Requests } from "./pages/Requests";
import { RequestForm } from "./pages/RequestForm";
import { Timetables } from "./pages/Timetables";
import { Teachers } from "./pages/Teachers";
import { RoomRegister } from "./pages/RoomRegister";
import { Clubs } from "./pages/Clubs";
import { ClubProfile } from "./pages/ClubProfile";
function Protected({
  children,
  mode,
}: {
  children: ReactNode;
  mode: "request" | "review" | "admin" | "staff";
}) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Loading label="Checking your session…" />;
  if (!user)
    return (
      <Navigate
        to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`}
        replace
      />
    );
  const allowed =
    mode === "admin"
      ? user.role === "ADMIN"
      : mode === "review"
        ? canReview(user.role)
        : mode === "staff"
          ? canViewFaculty(user.role)
          : canRequest(user.role);
  return allowed ? (
    children
  ) : (
    <div className="container page">
      <Notice>
        Your account cannot access this page. You can still browse classrooms
        and lecture theatres.
      </Notice>
    </div>
  );
}
export function App() {
  return (
    <AuthProvider>
      <RoutePosition />
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Finder />} />
          <Route path="rooms/:id" element={<RoomDetail />} />
          <Route
            path="teachers"
            element={
              <Protected mode="staff">
                <Teachers />
              </Protected>
            }
          />
          <Route path="clubs" element={<Clubs />} />
          <Route path="clubs/:slug" element={<ClubProfile />} />
          <Route path="login" element={<Login />} />
          <Route
            path="requests/new"
            element={
              <Protected mode="request">
                <RequestForm />
              </Protected>
            }
          />
          <Route
            path="requests"
            element={
              <Protected mode="request">
                <Requests />
              </Protected>
            }
          />
          <Route
            path="approvals"
            element={
              <Protected mode="review">
                <Requests review />
              </Protected>
            }
          />
          <Route
            path="admin/timetables"
            element={
              <Protected mode="admin">
                <Timetables />
              </Protected>
            }
          />
          <Route
            path="admin/rooms"
            element={
              <Protected mode="admin">
                <RoomRegister />
              </Protected>
            }
          />
          <Route
            path="*"
            element={
              <div className="container page">
                <h1>Page not found.</h1>
                <p>Use Room availability to return to the search.</p>
              </div>
            }
          />
        </Route>
      </Routes>
    </AuthProvider>
  );
}
