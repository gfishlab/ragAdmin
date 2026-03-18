import type { TaskRealtimeEvent } from '@/types/realtime'

export interface ParseProgressViewState {
  percent: number
  stageLabel: string
  message: string
  status: 'success' | 'warning' | 'exception'
  active: boolean
}

function normalizePercent(value: number): number {
  if (value < 0) {
    return 0
  }
  if (value > 100) {
    return 100
  }
  return value
}

export function isRealtimeEventTerminal(event: TaskRealtimeEvent | null | undefined): boolean {
  return event?.terminal === true
}

export function isParseActive(parseStatus: string | null | undefined, event?: TaskRealtimeEvent | null): boolean {
  if (event?.taskStatus === 'WAITING' || event?.taskStatus === 'RUNNING') {
    return true
  }
  return parseStatus === 'PENDING' || parseStatus === 'PROCESSING'
}

export function buildParseProgressState(
  parseStatus: string | null | undefined,
  event?: TaskRealtimeEvent | null,
): ParseProgressViewState {
  if (event) {
    if (event.taskStatus === 'SUCCESS') {
      return {
        percent: 100,
        stageLabel: '解析完成',
        message: event.message || '文档解析已完成',
        status: 'success',
        active: false,
      }
    }
    if (event.taskStatus === 'FAILED' || event.taskStatus === 'CANCELED') {
      return {
        percent: normalizePercent(event.progressPercent ?? 0),
        stageLabel: event.currentStepName || '解析失败',
        message: event.message || '文档解析失败',
        status: 'exception',
        active: false,
      }
    }
    if (event.taskStatus === 'WAITING') {
      return {
        percent: normalizePercent(event.progressPercent ?? 5),
        stageLabel: '排队中',
        message: event.message || '解析任务已进入队列',
        status: 'warning',
        active: true,
      }
    }
    return {
      percent: normalizePercent(event.progressPercent ?? 15),
      stageLabel: event.currentStepName || '处理中',
      message: event.message || '解析任务执行中',
      status: 'warning',
      active: true,
    }
  }

  if (parseStatus === 'SUCCESS') {
    return {
      percent: 100,
      stageLabel: '解析完成',
      message: '文档解析已完成',
      status: 'success',
      active: false,
    }
  }
  if (parseStatus === 'FAILED') {
    return {
      percent: 0,
      stageLabel: '解析失败',
      message: '文档解析失败，请查看错误详情后重试',
      status: 'exception',
      active: false,
    }
  }
  if (parseStatus === 'PROCESSING') {
    return {
      percent: 15,
      stageLabel: '处理中',
      message: '解析任务执行中，等待服务端推送最新阶段',
      status: 'warning',
      active: true,
    }
  }
  return {
    percent: 5,
    stageLabel: '排队中',
    message: '解析任务已进入队列，等待后台执行',
    status: 'warning',
    active: true,
  }
}

export function buildTaskProgressState(
  taskStatus: string | null | undefined,
  event?: TaskRealtimeEvent | null,
): ParseProgressViewState {
  if (event) {
    return buildParseProgressState(null, event)
  }

  if (taskStatus === 'SUCCESS') {
    return {
      percent: 100,
      stageLabel: '任务完成',
      message: '后台任务已执行完成',
      status: 'success',
      active: false,
    }
  }
  if (taskStatus === 'FAILED' || taskStatus === 'CANCELED') {
    return {
      percent: 0,
      stageLabel: '任务失败',
      message: '后台任务执行失败，请查看步骤与错误信息',
      status: 'exception',
      active: false,
    }
  }
  if (taskStatus === 'RUNNING') {
    return {
      percent: 15,
      stageLabel: '执行中',
      message: '后台任务正在执行，等待服务端推送当前阶段',
      status: 'warning',
      active: true,
    }
  }
  return {
    percent: 5,
    stageLabel: '排队中',
    message: '后台任务已进入队列，等待执行',
    status: 'warning',
    active: true,
  }
}
