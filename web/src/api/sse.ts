import { getAccessToken } from '@/utils/token'

export type ChatStreamPhase = 'reasoning' | 'answer'

export interface ChatStreamDelta {
  phase: ChatStreamPhase
  content: string
}

export interface StreamChatHandlers {
  onMeta?: (data: unknown) => void
  onDelta: (delta: ChatStreamDelta) => void
  onDone?: (data: unknown) => void
  onError?: (error: Error) => void
}

interface SseFrame {
  event: string
  data: string
}

interface DispatchResult {
  requestId?: string
  done?: boolean
  error?: Error
}

const FIRST_PACKET_TIMEOUT_MS = 8000

export async function streamChat(
  sessionId: number | string,
  content: string,
  handlers: StreamChatHandlers,
  signal?: AbortSignal,
  retry = false,
  mode?: 'STANDARD' | 'EXPERT'
): Promise<void> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const accessToken = getAccessToken()
  const requestController = new AbortController()
  let callerAborted = signal?.aborted ?? false
  let firstPacketReceived = false
  let firstPacketTimedOut = false
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined

  const abortFromCaller = (): void => {
    callerAborted = true
    requestController.abort()
  }
  if (callerAborted) {
    requestController.abort()
  } else {
    signal?.addEventListener('abort', abortFromCaller, { once: true })
  }

  const firstPacketTimer = setTimeout(() => {
    if (!firstPacketReceived) {
      firstPacketTimedOut = true
      requestController.abort()
    }
  }, FIRST_PACKET_TIMEOUT_MS)

  try {
    const response = await fetch(`${baseUrl}/chat/sessions/${sessionId}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {})
      },
      body: JSON.stringify({
        content,
        retry,
        ...(mode ? { mode } : {})
      }),
      signal: requestController.signal
    })

    if (!response.ok || !response.body) {
      throw new Error(`SSE 请求失败，HTTP ${response.status}`)
    }

    reader = response.body.getReader()
    const activeReader = reader
    const decoder = new TextDecoder()
    let buffer = ''
    let expectedRequestId = ''
    let doneReceived = false

    const processFrame = (frameText: string): void => {
      const frame = parseFrame(frameText)
      if (!frame) {
        return
      }
      const result = dispatchFrame(
        frame,
        handlers,
        sessionId,
        expectedRequestId
      )
      if (result.requestId) {
        expectedRequestId = result.requestId
      }
      if (result.done) {
        doneReceived = true
      }
      if (result.error) {
        void activeReader.cancel().catch(() => undefined)
        throw result.error
      }
    }

    const processFrames = (frames: string[]): void => {
      for (const frameText of frames) {
        if (frameText.trim()) {
          processFrame(frameText)
        }
      }
    }

    while (true) {
      const { done, value } = await activeReader.read()
      if (done) {
        break
      }
      if (!firstPacketReceived) {
        firstPacketReceived = true
        clearTimeout(firstPacketTimer)
      }

      buffer += decoder.decode(value, { stream: true })
      buffer = normalizeBuffer(buffer)
      const { frames, rest } = extractFrames(buffer, false)
      buffer = rest
      processFrames(frames)
    }

    buffer += decoder.decode()
    buffer = normalizeBuffer(buffer)
    const { frames } = extractFrames(buffer, true)
    processFrames(frames)

    if (!doneReceived && !callerAborted) {
      throw new Error('SSE 连接意外结束，请重试')
    }
  } catch (error) {
    if (callerAborted) {
      return
    }
    if (firstPacketTimedOut) {
      throw new Error('SSE 首包超时，请重试')
    }
    if (error instanceof DOMException && error.name === 'AbortError') {
      return
    }
    throw error
  } finally {
    clearTimeout(firstPacketTimer)
    signal?.removeEventListener('abort', abortFromCaller)
    reader?.releaseLock()
  }
}

function normalizeBuffer(value: string): string {
  return value.replace(/\r\n?/g, '\n')
}

function extractFrames(
  buffer: string,
  flush: boolean
): { frames: string[]; rest: string } {
  const parts = buffer.split('\n\n')
  if (flush) {
    return { frames: parts, rest: '' }
  }
  const rest = parts.pop() ?? ''
  return { frames: parts, rest }
}

function parseFrame(frameText: string): SseFrame | null {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of frameText.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  return {
    event,
    data: dataLines.join('\n')
  }
}

function dispatchFrame(
  frame: SseFrame,
  handlers: StreamChatHandlers,
  expectedSessionId: number | string,
  expectedRequestId: string
): DispatchResult {
  let payload: Record<string, unknown>
  try {
    payload = JSON.parse(frame.data) as Record<string, unknown>
  } catch {
    const error = new Error('无法解析 SSE 响应')
    notifyError(handlers, error)
    return { error }
  }

  const requestId = typeof payload.requestId === 'string' ? payload.requestId : undefined
  if (expectedRequestId && requestId && requestId !== expectedRequestId) {
    return { requestId: expectedRequestId }
  }
  if (
    payload.sessionId !== undefined &&
    Number(payload.sessionId) !== Number(expectedSessionId)
  ) {
    return { requestId }
  }

  if (frame.event === 'meta') {
    handlers.onMeta?.(payload)
    return { requestId }
  }
  if (frame.event === 'delta') {
    const deltaContent = typeof payload.content === 'string' ? payload.content : ''
    if (deltaContent) {
      handlers.onDelta({
        phase: payload.phase === 'reasoning' ? 'reasoning' : 'answer',
        content: deltaContent
      })
    }
    return { requestId }
  }
  if (frame.event === 'done') {
    handlers.onDone?.(payload)
    return { requestId, done: true }
  }
  if (frame.event === 'error') {
    const error = new Error(String(payload.message ?? 'AI 服务异常'))
    const streamError = error as Error & { code?: string }
    streamError.code = String(payload.code ?? 'CHAT_STREAM_FAILED')
    notifyError(handlers, error)
    return { requestId, error }
  }
  return { requestId }
}

function notifyError(handlers: StreamChatHandlers, error: Error): void {
  try {
    handlers.onError?.(error)
  } catch {
    // The stream error is authoritative; a broken listener must not mask it.
  }
}
