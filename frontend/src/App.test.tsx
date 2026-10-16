import { render, screen } from '@testing-library/react'
import { App } from '@/App'

describe('App', () => {
  it('shows the name of the product as the page title', () => {
    render(<App />)
    expect(screen.getByRole('heading', { level: 1, name: 'Paraphe' })).toBeInTheDocument()
  })
})
