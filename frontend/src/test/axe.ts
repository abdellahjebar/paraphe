import axe from 'axe-core'

/**
 * Runs the automatic accessibility checks on a rendered piece of the interface. Colour contrast is left out
 * on purpose: jsdom draws no colours, and the theme's contrast is measured separately (design decision 15).
 */
export async function accessibilityViolations(container: HTMLElement): Promise<string[]> {
  const result = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  return result.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.length} node(s))`)
}
