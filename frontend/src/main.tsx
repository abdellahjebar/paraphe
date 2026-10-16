import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from '@/App'
import { createAppRouter } from '@/app/router'
import { createQueryClient } from '@/lib/query'
import '@/styles/index.css'

const root = document.getElementById('root')
if (!root) throw new Error('Missing #root element')

const queryClient = createQueryClient()
const router = createAppRouter()

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
