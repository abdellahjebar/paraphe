import {
  applyTheme,
  DEFAULT_THEME,
  isDark,
  isTheme,
  readTheme,
  saveTheme,
  THEME_KEY,
} from '@/lib/theme/theme'

function mockSystemDark(dark: boolean) {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockReturnValue({ matches: dark, addEventListener: vi.fn(), removeEventListener: vi.fn() }),
  )
}

describe('theme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    mockSystemDark(false)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('is light until the person chooses otherwise', () => {
    expect(DEFAULT_THEME).toBe('light')
    expect(readTheme()).toBe('light')
  })

  it('ignores anything stored that is not a known theme', () => {
    localStorage.setItem(THEME_KEY, 'purple')
    expect(isTheme('purple')).toBe(false)
    expect(readTheme()).toBe('light')
  })

  it('follows the system only when asked to', () => {
    mockSystemDark(true)
    expect(isDark('light')).toBe(false)
    expect(isDark('dark')).toBe(true)
    expect(isDark('system')).toBe(true)
  })

  it('puts the dark class on the page and remembers the choice', () => {
    saveTheme('dark')
    expect(document.documentElement).toHaveClass('dark')
    expect(readTheme()).toBe('dark')

    saveTheme('light')
    expect(document.documentElement).not.toHaveClass('dark')
  })

  it('still applies the choice when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(() => saveTheme('dark')).not.toThrow()
    expect(document.documentElement).toHaveClass('dark')
    vi.restoreAllMocks()
  })

  it('applies a theme without saving it', () => {
    applyTheme('dark')
    expect(document.documentElement).toHaveClass('dark')
    expect(localStorage.getItem(THEME_KEY)).toBeNull()
  })
})
