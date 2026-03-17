export const TASK_TYPE_DOCUMENT_PARSE = 'DOCUMENT_PARSE'
export const TASK_BIZ_TYPE_KB_DOCUMENT = 'KB_DOCUMENT'

export const TASK_TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '文档解析', value: TASK_TYPE_DOCUMENT_PARSE },
]

export const TASK_STATUS_OPTIONS = [
  { label: '等待中', value: 'WAITING' },
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELED' },
]

export const DOCUMENT_PARSE_STATUS_OPTIONS = [
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]

export const TASK_BIZ_TYPE_OPTIONS = [
  { label: '知识库文档', value: TASK_BIZ_TYPE_KB_DOCUMENT },
]

export const TASK_RETRY_RESULT_OPTIONS = [
  { label: '已提交', value: 'SUBMITTED' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELED' },
]

export const RESOURCE_STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

export function formatTaskType(taskType: string | null | undefined): string {
  if (!taskType) {
    return '暂无'
  }
  const matched = TASK_TYPE_OPTIONS.find((item) => item.value === taskType)
  return matched?.label ?? taskType
}

export function formatTaskStatus(taskStatus: string | null | undefined): string {
  if (!taskStatus) {
    return '暂无'
  }
  const matched = TASK_STATUS_OPTIONS.find((item) => item.value === taskStatus)
  return matched?.label ?? taskStatus
}

export function formatTaskBizType(bizType: string | null | undefined): string {
  if (!bizType) {
    return '暂无'
  }
  const matched = TASK_BIZ_TYPE_OPTIONS.find((item) => item.value === bizType)
  return matched?.label ?? bizType
}

export function formatDocumentParseStatus(parseStatus: string | null | undefined): string {
  if (!parseStatus) {
    return '暂无'
  }
  const matched = DOCUMENT_PARSE_STATUS_OPTIONS.find((item) => item.value === parseStatus)
  return matched?.label ?? parseStatus
}

export function formatTaskRetryResult(retryResult: string | null | undefined): string {
  if (!retryResult) {
    return '暂无'
  }
  const matched = TASK_RETRY_RESULT_OPTIONS.find((item) => item.value === retryResult)
  return matched?.label ?? retryResult
}

export function formatResourceStatus(status: string | null | undefined): string {
  if (!status) {
    return '暂无'
  }
  const matched = RESOURCE_STATUS_OPTIONS.find((item) => item.value === status)
  return matched?.label ?? status
}
