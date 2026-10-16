import { expect, test } from '@playwright/test'

/*
 * Needs a backend started with SESSION_IDLE_TIMEOUT=90s, so that the warning (30 seconds before the end)
 * and the end can be seen without waiting an hour. The waits are real: this test takes about two minutes.
 */
const ADMIN = {
  email: process.env.E2E_ADMIN_EMAIL ?? 'admin@exemple.test',
  password: process.env.E2E_ADMIN_PASSWORD ?? '',
}

test('warns before the session ends, lets the person stay, then ends it when nobody answers', async ({
  page,
}) => {
  test.setTimeout(240_000)
  expect(ADMIN.password, 'E2E_ADMIN_PASSWORD must be set').not.toBe('')

  await page.goto('/connexion')
  await page.getByLabel('Adresse e-mail').fill(ADMIN.email)
  await page.getByLabel('Mot de passe', { exact: true }).fill(ADMIN.password)
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await expect(page).toHaveURL(/\/projets$/)

  const warning = page.getByRole('alertdialog', { name: 'Votre session va se terminer' })

  // an idle person: nothing at first, then the warning with the time left
  await expect(warning).toHaveCount(0)
  await expect(warning).toBeVisible({ timeout: 90_000 })
  await expect(warning.getByRole('timer')).toHaveText(/^0:[0-3]\d$/)
  await expect(warning.getByRole('button', { name: 'Rester connecté' })).toBeFocused()

  // "I am here": the server starts its clock again, and the page is still the person's
  await warning.getByRole('button', { name: 'Rester connecté' }).click()
  await expect(warning).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { level: 1, name: 'Mes chantiers' })).toBeVisible()

  // nobody answers this time: the server ends the session and the page says so
  await expect(warning).toBeVisible({ timeout: 90_000 })
  await expect(page).toHaveURL(/\/connexion$/, { timeout: 60_000 })
  await expect(page.getByRole('status')).toContainText('Votre session est terminée.')
})
