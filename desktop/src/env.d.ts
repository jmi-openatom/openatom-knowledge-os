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

interface Window {
  openatom?: {
    platform: string
    auth: {
      login: () => Promise<{ user: OpenAtomUser; expiresIn: number }>
      logout: () => Promise<boolean>
      getSession: () => Promise<{ user: OpenAtomUser; expiresIn: number; obtainedAt: number } | null>
      getAccessToken: () => Promise<string | null>
      refresh: () => Promise<{ user: OpenAtomUser; expiresIn: number } | null>
    }
    updater: {
      check: () => Promise<unknown>
      onStatus: (listener: (payload: unknown) => void) => () => void
    }
    preview: {
      open: (fileId: number) => Promise<void>
      onNavigate: (listener: (payload: { fileId: number }) => void) => () => void
    }
  }
}
