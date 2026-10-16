import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { App } from '@/App'
import { createQueryClient } from '@/lib/query'

describe('App', () => {
  it('opens on the sign-in page', () => {
    render(
      <QueryClientProvider client={createQueryClient()}>
        <App />
      </QueryClientProvider>,
    )
    expect(screen.getByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument()
  })
})
