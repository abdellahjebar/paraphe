import { createBrowserRouter, Navigate, Outlet, type RouteObject } from 'react-router'
import {
  RedirectIfSignedIn,
  RequireSession,
  HOME_PATH,
  LOGIN_PATH,
  PASSWORD_PATH,
} from '@/features/auth/guards'
import { useSessionEndedListener } from '@/features/auth/session'
import { ChangePasswordPage } from '@/routes/ChangePasswordPage'
import { LoginPage } from '@/routes/LoginPage'
import { NotFoundPage } from '@/routes/NotFoundPage'
import { ProjectsPage } from '@/routes/ProjectsPage'
import { ReviewPage } from '@/routes/ReviewPage'
import { ThemeCheck } from '@/routes/ThemeCheck'

/** The top of the tree: what must run wherever the person is. */
function RootLayout() {
  // When the server ends the session, the interface forgets the account at once, on whatever screen.
  useSessionEndedListener()
  return <Outlet />
}

/**
 * Every address of the interface. The reviewer's page is public and never asks about a session; the
 * contractor's pages all need one. The server serves the same entry page for each of these paths
 * (SpaController), so a reload or a pasted link works.
 */
export const routes: RouteObject[] = [
  {
    element: <RootLayout />,
    children: [
      { path: '/', element: <Navigate to={HOME_PATH} replace /> },
      { element: <RedirectIfSignedIn />, children: [{ path: LOGIN_PATH, element: <LoginPage /> }] },
      {
        element: <RequireSession />,
        children: [
          { path: PASSWORD_PATH, element: <ChangePasswordPage /> },
          { path: HOME_PATH, element: <ProjectsPage /> },
        ],
      },
      { path: '/relecture/:token', element: <ReviewPage /> },
      // The theme page is a development tool: it does not exist in the built application.
      ...(import.meta.env.DEV ? [{ path: '/theme', element: <ThemeCheck /> }] : []),
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export function createAppRouter() {
  return createBrowserRouter(routes)
}
