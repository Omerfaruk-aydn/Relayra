import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function RequireAuth() {
  const { accessToken, user } = useAuth();
  const location = useLocation();
  if (!accessToken || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}

export function RequireGuest() {
  const { accessToken, user } = useAuth();
  if (accessToken && user) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
