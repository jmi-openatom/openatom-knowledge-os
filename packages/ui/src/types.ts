export type OaSize = 'sm' | 'md' | 'lg'
export type OaTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger'

export interface SelectOption {
  label: string
  value: string | number
  disabled?: boolean
}

export interface ToastMessage {
  id: number
  title: string
  description?: string
  tone?: OaTone
  duration?: number
}
