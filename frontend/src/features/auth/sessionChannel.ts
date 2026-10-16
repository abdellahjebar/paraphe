import type { LoginReason } from '@/features/auth/session'

/**
 * What tabs of the same browser tell each other, so that one tab never warns about a session another tab is
 * keeping alive, and signing out in one tab signs out all of them.
 */
export type SessionMessage = { type: 'contact'; at: number } | { type: 'ended'; reason: LoginReason }

const NAME = 'paraphe-session'

type Listener = (message: SessionMessage) => void

let channel: BroadcastChannel | undefined
const listeners = new Set<Listener>()

function open(): BroadcastChannel | undefined {
  if (typeof BroadcastChannel === 'undefined') return undefined
  if (!channel) {
    channel = new BroadcastChannel(NAME)
    channel.onmessage = (event: MessageEvent<SessionMessage>) =>
      listeners.forEach((listener) => listener(event.data))
  }
  return channel
}

export function broadcastSession(message: SessionMessage): void {
  open()?.postMessage(message)
}

export function listenToSession(listener: Listener): () => void {
  open()
  listeners.add(listener)
  return () => listeners.delete(listener)
}
