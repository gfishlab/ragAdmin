import type { ApiResponse, PageResponse } from '@/types/api'
import type { FeedbackListQuery, FeedbackRecord } from '@/types/feedback'
import { http, unwrapResponse } from './http'

export async function listFeedback(
  query: FeedbackListQuery,
): Promise<PageResponse<FeedbackRecord>> {
  const response = await http.get<ApiResponse<PageResponse<FeedbackRecord>>>(
    '/admin/chat-feedback',
    {
      params: {
        feedbackType: query.feedbackType || undefined,
        startTime: query.startTime || undefined,
        endTime: query.endTime || undefined,
        pageNo: query.pageNo ?? 1,
        pageSize: query.pageSize ?? 20,
      },
    },
  )
  return unwrapResponse(response.data)
}
