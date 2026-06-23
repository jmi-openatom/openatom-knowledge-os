/// <reference types="vite/client" />

interface OpenAtomUser {
  sub?: string
  id?: number
  name: string
  email?: string
  avatar?: string
  roles?: string[]
  permissions?: string[]
}

interface UpdateStatusPayload {
  status: 'idle' | 'checking' | 'available' | 'downloading' | 'downloaded' | 'current' | 'error' | 'dev-mode' | 'disabled-in-development'
  percent?: number
  transferred?: number
  total?: number
  info?: { version?: string; [key: string]: any }
  message?: string
}

interface Window {
  openatom?: {
    platform: string
    app: {
      getVersion: () => Promise<string>
    }
    auth: {
      login: () => Promise<{ user: OpenAtomUser; expiresIn: number }>
      logout: () => Promise<boolean>
      getSession: () => Promise<{ user: OpenAtomUser; expiresIn: number; obtainedAt: number } | null>
      getAccessToken: () => Promise<string | null>
      refresh: () => Promise<{ user: OpenAtomUser; expiresIn: number } | null>
    }
    updater: {
      check: () => Promise<{ status?: string; [key: string]: any } | null>
      install: () => Promise<void>
      onStatus: (listener: (payload: UpdateStatusPayload) => void) => () => void
    }
    preview: {
      open: (fileId: number) => Promise<void>
      onNavigate: (listener: (payload: { fileId: number }) => void) => () => void
    }
  }
}
