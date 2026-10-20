import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { api, ApiError } from "./api";
import type { User } from "./types";
interface AuthContext {
  user: User | null;
  loading: boolean;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
}
const Auth = createContext<AuthContext | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    api
      .me()
      .then((s) => setUser(s.user))
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);
  const login = async (email: string, password: string) => {
    const result = await api.login(email, password);
    setUser(result.user);
  };
  const logout = async () => {
    try {
      await api.logout();
      setUser(null);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setUser(null);
      else throw error;
    }
  };
  return (
    <Auth.Provider value={{ user, loading, login, logout }}>
      {children}
    </Auth.Provider>
  );
}
export function useAuth() {
  const context = useContext(Auth);
  if (!context) throw new Error("AuthProvider is missing");
  return context;
}
export function canReview(role?: string) {
  return ["COORDINATOR", "ALLOCATION_FACULTY", "HOD"].includes(role || "");
}
export function canRequest(role?: string) {
  return ["FACULTY", "CLUB_HEAD", "COORDINATOR", "HOD"].includes(role || "");
}
export function canViewFaculty(role?: string) {
  return ["FACULTY", "COORDINATOR", "HOD", "ADMIN"].includes(role || "");
}
