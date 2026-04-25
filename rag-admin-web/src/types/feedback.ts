import type { PageResponse } from './api'

export interface FeedbackRecord {
  id: number
  messageId: number
  userId: number
  username: string
  feedbackType: string
  commentText: string | null
  questionSummary: string | null
  answerSummary: string | null
  sessionId: number
  createdAt: string
}

export interface FeedbackListQuery {
  feedbackType?: string
  startTime?: string
  endTime?: string
  pageNo?: number
  pageSize?: number
}

export type FeedbackPage = PageResponse<FeedbackRecord>
