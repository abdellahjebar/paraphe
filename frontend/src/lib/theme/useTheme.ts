import { useSyncExternalStore } from 'react'
import { isDark, readTheme, THEME_EVENT, type Theme } from '@/lib/theme/theme'

function subscribe(onChange: () => void) {
  window.addEventListener(THEME_EVENT, onChange)
  window.addEventListener('storage', onChange)
  return () => {
    window.removeEventListener(THEME_EVENT, onChange)
    window.removeEventListener('storage', onChange)
  }
}

/** The choice the person made: light, dark or system. */
export function useThemeChoice(): Theme {
  return useSyncExternalStore(subscribe, readTheme, () => 'light')
}

/** What is actually shown right now: light or dark. */
export function useResolvedTheme(): 'light' | 'dark' {
  return useSyncExternalStore(
    subscribe,
    () => (isDark(readTheme()) ? 'dark' : 'light'),
    () => 'light',
  )
}
