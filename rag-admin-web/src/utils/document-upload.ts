const DOC_TYPE_BY_EXTENSION: Record<string, string> = {
  pdf: 'PDF',
  docx: 'DOCX',
  pptx: 'PPTX',
  xlsx: 'XLSX',
  md: 'MARKDOWN',
  markdown: 'MARKDOWN',
  txt: 'TXT',
  png: 'PNG',
  jpg: 'JPG',
  jpeg: 'JPEG',
  webp: 'WEBP',
}

const OCR_IMAGE_DOC_TYPES = new Set(['PNG', 'JPG', 'JPEG', 'WEBP'])

export const DOCUMENT_UPLOAD_ACCEPT =
  '.txt,.md,.markdown,.pdf,.docx,.pptx,.xlsx,.png,.jpg,.jpeg,.webp'

export function inferDocumentType(fileName: string): string {
  const extension = fileName.split('.').pop()?.toLowerCase() ?? ''
  return DOC_TYPE_BY_EXTENSION[extension] ?? 'UNKNOWN'
}

export function isOcrImageDocumentType(docType: string): boolean {
  return OCR_IMAGE_DOC_TYPES.has(docType)
}

export function isPdfDocumentType(docType: string): boolean {
  return docType === 'PDF'
}
