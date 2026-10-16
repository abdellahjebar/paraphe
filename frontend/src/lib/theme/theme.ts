/**
 * The theme is a per-person convenience: light by default, dark when chosen, or following the system.
 * It is kept in the browser only, and every access is guarded because storage can be blocked.
 * public/theme-init.js applies the same choice before the first paint.
 */
export type Theme = 'light' | 'dark' | 'system'

export const THEME_KEY = 'paraphe-theme'
export const DEFAULT_THEME: Theme = 'light'

export function isTheme(value: unknown): value is Theme {
  return value === 'light' || value === 'dark' || value === 'system'
}

export function readTheme(): Theme {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    return isTheme(saved) ? saved : DEFAULT_THEME
  } catch {
    return DEFAULT_THEME
  }
}

export function prefersDark(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
}

export function isDark(theme: Theme): boolean {
  return theme === 'dark' || (theme === 'system' && prefersDark())
}

export function applyTheme(theme: Theme): void {
  const root = document.documentElement
  root.classList.toggle('dark', isDark(theme))
}

export function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(THEME_KEY, theme)
  } catch {
    /* the choice still applies for this visit */
  }
  applyTheme(theme)
}
