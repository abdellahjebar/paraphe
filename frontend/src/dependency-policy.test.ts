import pkg from '../package.json'

/**
 * Packages the shadcn command-line tool tends to add, or that we decided against.
 * Each one is replaced by something already in the project; see docs/frontend/components.md.
 */
const FORBIDDEN: Record<string, string> = {
  cn: 'we have our own cn() in src/lib/utils.ts (clsx + tailwind-merge)',
  'next-themes': 'we have our own theme module in src/lib/theme',
  shadcn: 'its stylesheet is vendored in src/styles/shadcn-base.css; the CLI is run with npx only',
  '@fontsource-variable/geist': 'the interface font is Source Sans 3, self-hosted in src/assets/fonts',
}

describe('dependency policy', () => {
  it.each(Object.entries(FORBIDDEN))('does not depend on %s', (name, why) => {
    const all = { ...pkg.dependencies, ...pkg.devDependencies } as Record<string, string>
    expect(all[name], `${name}: ${why}`).toBeUndefined()
  })
})
