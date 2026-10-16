import { expect, test, type Page } from '@playwright/test'

/*
 * Accounts of the end-to-end database, created when the backend starts (docs/ops/local-setup.md).
 * They exist only in a throw-away local database; none of this is real.
 */
const ADMIN = {
  email: process.env.E2E_ADMIN_EMAIL ?? 'admin@exemple.test',
  password: process.env.E2E_ADMIN_PASSWORD ?? '',
}
const NEWCOMER = {
  email: process.env.E2E_NEW_EMAIL ?? 'nouvelle@exemple.test',
  temporary: process.env.E2E_NEW_PASSWORD ?? '',
  chosen: 'une phrase choisie pour les essais',
}

async function fillSignIn(page: Page, email: string, password: string) {
  await page.getByLabel('Adresse e-mail').fill(email)
  await page.getByLabel('Mot de passe', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Se connecter' }).click()
}

test.describe('sign-in against the real backend', () => {
  test.beforeAll(() => {
    expect(ADMIN.password, 'E2E_ADMIN_PASSWORD must be set').not.toBe('')
    expect(NEWCOMER.temporary, 'E2E_NEW_PASSWORD must be set').not.toBe('')
  })

  test('sends a visitor who is not signed in to the sign-in page', async ({ page }) => {
    await page.goto('/projets')

    await expect(page).toHaveURL(/\/connexion$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Connexion' })).toBeVisible()
    await expect(page).toHaveTitle('Connexion – Paraphe')
    // the session cookie is the server's alone: a script cannot read it
    expect(await page.evaluate(() => document.cookie)).not.toContain('JSESSIONID')
  })

  test('lists what is missing without calling the server', async ({ page }) => {
    const calls: string[] = []
    page.on('request', (request) => {
      if (request.method() === 'POST') calls.push(request.url())
    })
    await page.goto('/connexion')

    await page.getByRole('button', { name: 'Se connecter' }).click()

    await expect(page.getByRole('alert')).toContainText('Saisissez votre adresse e-mail.')
    await expect(page.getByRole('alert')).toBeFocused()
    expect(calls).toEqual([])
  })

  test('answers a wrong password and an unknown account in exactly the same words', async ({ page }) => {
    await page.goto('/connexion')
    await fillSignIn(page, ADMIN.email, 'ce n’est pas le bon mot de passe')
    const wrongPassword = await page.getByRole('alert').innerText()

    await page.goto('/connexion')
    await fillSignIn(page, 'personne@exemple.test', 'ce n’est pas le bon mot de passe')
    const unknownAccount = await page.getByRole('alert').innerText()

    expect(wrongPassword).toContain('L’adresse e-mail ou le mot de passe est incorrect.')
    expect(unknownAccount).toBe(wrongPassword)
  })

  test('signs in, shows the sites, signs out and stays out', async ({ page }) => {
    await page.goto('/connexion')
    await fillSignIn(page, ADMIN.email, ADMIN.password)

    await expect(page).toHaveURL(/\/projets$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Mes chantiers' })).toBeVisible()

    await page.getByRole('button', { name: 'Se déconnecter' }).click()
    await expect(page).toHaveURL(/\/connexion$/)
    await expect(page.getByRole('status')).toContainText('Vous avez été déconnecté.')

    // the server really ended the session: asking again for a protected page sends back to sign-in
    await page.goto('/projets')
    await expect(page).toHaveURL(/\/connexion$/)
  })

  test('survives a reload: the session is the server’s, not the page’s', async ({ page }) => {
    await page.goto('/connexion')
    await fillSignIn(page, ADMIN.email, ADMIN.password)
    await expect(page).toHaveURL(/\/projets$/)

    await page.reload()

    await expect(page.getByRole('heading', { level: 1, name: 'Mes chantiers' })).toBeVisible()
  })

  test('makes a new account choose its own password before anything else', async ({ page }) => {
    await page.goto('/connexion')
    await fillSignIn(page, NEWCOMER.email, NEWCOMER.temporary)

    await expect(page).toHaveURL(/\/mot-de-passe$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Choisissez votre mot de passe' })).toBeVisible()

    // it cannot go anywhere else
    await page.goto('/projets')
    await expect(page).toHaveURL(/\/mot-de-passe$/)

    const newPassword = page.getByLabel('Nouveau mot de passe')
    const change = page.getByRole('button', { name: 'Enregistrer et continuer' })

    // too short: the server says why, with its own bound
    await page.getByLabel('Mot de passe provisoire').fill(NEWCOMER.temporary)
    await newPassword.fill('trop court')
    await change.click()
    await expect(page.getByRole('alert')).toContainText('au moins 15 caractères')

    // a famous password
    await newPassword.fill('passwordpassword')
    await change.click()
    await expect(page.getByRole('alert')).toContainText('trop courant')

    // the wrong temporary password
    await page.getByLabel('Mot de passe provisoire').fill('pas le bon mot de passe provisoire')
    await newPassword.fill(NEWCOMER.chosen)
    await change.click()
    await expect(page.getByRole('alert')).toContainText('Le mot de passe actuel est incorrect.')

    // the right one
    await page.getByLabel('Mot de passe provisoire').fill(NEWCOMER.temporary)
    await change.click()
    await expect(page).toHaveURL(/\/projets$/)
  })

  test('accepts the chosen password afterwards and refuses the temporary one', async ({ page }) => {
    await page.goto('/connexion')
    await fillSignIn(page, NEWCOMER.email, NEWCOMER.temporary)
    await expect(page.getByRole('alert')).toContainText('incorrect')

    await page.getByLabel('Mot de passe', { exact: true }).fill(NEWCOMER.chosen)
    await page.getByRole('button', { name: 'Se connecter' }).click()
    await expect(page).toHaveURL(/\/projets$/)
  })

  test('shows the reviewer page without asking the server about any session', async ({ page }) => {
    const api: string[] = []
    page.on('request', (request) => {
      if (new URL(request.url()).pathname.startsWith('/api/')) api.push(request.url())
    })

    await page.goto('/relecture/un-jeton-de-test-qui-ne-sert-a-rien')

    await expect(page.getByRole('heading', { level: 1, name: 'Relecture' })).toBeVisible()
    expect(api).toEqual([])
    await expect(page.locator('body')).not.toContainText('un-jeton-de-test')
  })

  test('answers an unknown address with a page that says so', async ({ page }) => {
    await page.goto('/une/adresse/inconnue')

    await expect(page.getByRole('heading', { level: 1, name: 'Cette page n’existe pas' })).toBeVisible()
  })
})
