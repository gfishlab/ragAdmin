export const TASK_TYPE_DOCUMENT_PARSE = 'DOCUMENT_PARSE'

export const TASK_TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '文档解析', value: TASK_TYPE_DOCUMENT_PARSE },
]

export function formatTaskType(taskType: string | null | undefined): string {
  if (!taskType) {
    return '暂无'
  }
  const matched = TASK_TYPE_OPTIONS.find((item) => item.value === taskType)
  return matched?.label ?? taskType
}
