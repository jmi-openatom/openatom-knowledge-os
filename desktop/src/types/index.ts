export interface KnowledgeUser {
  id?: number
  sub?: string
  name: string
  email?: string
  avatar?: string
  role: 'admin' | 'leader' | 'member' | 'guest'
  roles?: string[]
  permissions?: string[]
}

export interface Conversation {
  id: number
  title: string
  updatedAt: string
  group: string
}

export interface SourceReference {
  id: number
  title: string
  fileName: string
  excerpt: string
  score: number
  type: string
  path: string
  createdBy?: string
  updatedAt?: string
  size?: number
  extension?: string
}

export interface FilePreview {
  id: number
  name: string
  extension: string
  size: number
  contentType: string
  createdBy: string
  updatedAt: string
  status: string
  extractedText: string
  previewType: 'markdown' | 'text' | 'pdf' | 'image' | 'docx' | 'xlsx' | 'pptx'
}

export interface ChatAnswer {
  id: number
  question: string
  answer: string
  sources: SourceReference[]
  createdAt: string
}

export interface KnowledgeFile {
  id: number
  name: string
  extension: string
  size: number
  tags: string[]
  status: 'READY' | 'PARSING' | 'FAILED'
  createdBy: string
  updatedAt: string
}

export interface KnowledgeDocument {
  id: number
  title: string
  content: string
  version: number
  updatedAt: string
  updatedBy: string
}

export interface DocumentVersion {
  id: number
  documentId: number
  version: number
  title: string
  content: string
  createdAt: string
  createdByName: string
}

export interface WikiNode {
  id: number
  parentId?: number | null
  title: string
  type: 'category' | 'page'
  content?: string
  sortOrder?: number
  updatedByName?: string
  updatedAt?: string
  children?: WikiNode[]
}
