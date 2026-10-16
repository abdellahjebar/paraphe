import { LoginPage } from '@/routes/LoginPage'
import { ThemeCheck } from '@/routes/ThemeCheck'

/** Temporary: the router arrives with the shell. Until then #theme shows the theme page. */
export function App() {
  if (window.location.hash === '#theme') return <ThemeCheck />
  return <LoginPage onSignedIn={() => undefined} />
}
