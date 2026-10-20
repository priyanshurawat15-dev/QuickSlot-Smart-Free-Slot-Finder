import { useEffect } from "react";
import { useLocation } from "react-router-dom";
export function RoutePosition() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0 });
  }, [pathname]);
  return null;
}
