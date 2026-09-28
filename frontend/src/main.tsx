import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { RequireAuth, RequireGuest } from "./auth/guards";
import { AppShell } from "./shell/AppShell";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { HomePage } from "./pages/HomePage";
import { FriendsPage } from "./pages/FriendsPage";
import { FriendRequestsPage } from "./pages/FriendRequestsPage";
import { AddFriendsPage } from "./pages/AddFriendsPage";
import { BlockedUsersPage } from "./pages/BlockedUsersPage";
import { CommunitiesPage } from "./pages/CommunitiesPage";
import { SettingsPage } from "./pages/SettingsPage";
import "./styles/tokens.css";
import "./styles/app.css";

const router = createBrowserRouter([
  {
    element: <RequireGuest />,
    children: [
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: "/", element: <HomePage /> },
          { path: "/friends", element: <FriendsPage /> },
          { path: "/friends/requests", element: <FriendRequestsPage /> },
          { path: "/friends/add", element: <AddFriendsPage /> },
          { path: "/friends/blocked", element: <BlockedUsersPage /> },
          { path: "/communities", element: <CommunitiesPage /> },
          { path: "/settings", element: <SettingsPage /> },
        ],
      },
    ],
  },
]);

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </React.StrictMode>,
);
