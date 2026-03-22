import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { subscribeTaskEvents } from '@/api/realtime'
import { getAccessToken } from '@/utils/token-storage'

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn(),
}))

vi.mock('@/utils/token-storage', () => ({
  getAccessToken: vi.fn(),
}))

const fetchEventSourceMock = vi.mocked(fetchEventSource)
const getAccessTokenMock = vi.mocked(getAccessToken)

describe('subscribeTaskEvents', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAccessTokenMock.mockReturnValue('admin-token')
  })

  it('应携带鉴权头并只按 data JSON 分发业务事件', () => {
    let capturedOptions: Record<string, unknown> | undefined
    fetchEventSourceMock.mockImplementation((url, options) => {
      expect(url).toBe('/api/admin/events/tasks')
      capturedOptions = options as Record<string, unknown>
      return Promise.resolve()
    })

    const onEvent = vi.fn()

    subscribeTaskEvents({ onEvent })

    expect(fetchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(capturedOptions).toMatchObject({
      method: 'GET',
      openWhenHidden: true,
      headers: {
        Authorization: 'Bearer admin-token',
        Accept: 'text/event-stream',
      },
    })

    const onmessage = capturedOptions?.onmessage as ((message: unknown) => void) | undefined
    expect(onmessage).toBeTypeOf('function')

    onmessage?.({
      data: JSON.stringify({
        eventType: 'TASK_STARTED',
        taskId: 101,
        message: '开始处理文档',
      }),
      event: 'ignored_event',
      id: '999',
    })

    expect(onEvent).toHaveBeenCalledWith({
      eventType: 'TASK_STARTED',
      taskId: 101,
      message: '开始处理文档',
    })
  })

  it('当 fetchEventSource Promise 被拒绝时应回调 onError', async () => {
    const error = new Error('stream rejected')
    fetchEventSourceMock.mockRejectedValue(error)

    const onError = vi.fn()

    subscribeTaskEvents({
      onEvent: vi.fn(),
      onError,
    })

    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(onError).toHaveBeenCalledWith(error)
  })
})
