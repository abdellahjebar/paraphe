// Run after every `npx shadcn add ...`: brings the generated components back in line with our decisions.
// See docs/frontend/components.md. It is safe to run twice.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const dir = new URL('../src/components/ui/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')

const RULES = [
  // We use our own cn() (clsx + tailwind-merge), not a separate package.
  [/from ["']cn["']/g, "from '@/lib/utils'"],
  // A focus ring at half opacity does not reach 3:1 against the page; the full ring does.
  [/focus-visible:ring-ring\/50/g, 'focus-visible:ring-ring'],
]

let changed = 0
for (const name of readdirSync(dir).filter((f) => f.endsWith('.tsx'))) {
  const path = join(dir, name)
  const before = readFileSync(path, 'utf8')
  const after = RULES.reduce((text, [pattern, replacement]) => text.replace(pattern, replacement), before)
  if (after !== before) {
    writeFileSync(path, after)
    changed += 1
    console.log(`adjusted ${name}`)
  }
}
console.log(
  changed === 0
    ? 'nothing to adjust'
    : `${changed} file(s) adjusted: run "npm run format" and check the new component`,
)
