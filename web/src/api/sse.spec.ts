import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/token', () => ({
  getAccessToken: () => 'test-token'
}))

import { streamChat } from '@/api/sse'

function frame(event: string, payload: unknown): string {
  return `event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`
}

function streamResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder()
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk))
      }
      controller.close()
    }
  })
  return new Response(body, { status })
}

function failingStreamResponse(error: Error): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.error(error)
    }
  })
  return new Response(body, { status: 200 })
}

describe('streamChat 的 SSE 处理', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('按事件名分发 meta / delta / done，并保留回答阶段', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          frame('meta', { sessionId: 1, requestId: 'req-1' }),
          frame('delta', { content: '你', phase: 'answer', requestId: 'req-1' }),
          frame('delta', { content: '好', phase: 'answer', requestId: 'req-1' }),
          frame('done', { messageId: 2, requestId: 'req-1' })
        ])
      )
    )
    const deltas: Array<{ phase: string; content: string }> = []
    const onMeta = vi.fn()
    const onDone = vi.fn()

    await streamChat(1, '你好', {
      onMeta,
      onDelta: (delta) => deltas.push(delta),
      onDone
    })

    expect(onMeta).toHaveBeenCalledWith({ sessionId: 1, requestId: 'req-1' })
    expect(deltas).toEqual([
      { phase: 'answer', content: '你' },
      { phase: 'answer', content: '好' }
    ])
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('被网络分块截断的帧仍能完整解析', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          'event: delta\ndata: {"con',
          'tent":"半","phase":"answer","requestId":"req-1"}\n\n',
          frame('done', { requestId: 'req-1' })
        ])
      )
    )
    const deltas: string[] = []

    await streamChat(1, '继续', { onDelta: (delta) => deltas.push(delta.content) })

    expect(deltas).toEqual(['半'])
  })

  it('流结束时补发没有尾随空行的残帧', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          'event: done\ndata: {"messageId":1,"requestId":"req-1"}'
        ])
      )
    )
    const onDone = vi.fn()

    await streamChat(1, '继续', { onDelta: () => {}, onDone })

    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('reasoning_content 作为独立推理阶段下发，不拼入回答', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          frame('delta', { content: '先想', phase: 'reasoning', requestId: 'req-1' }),
          frame('delta', { content: '答案', phase: 'answer', requestId: 'req-1' }),
          frame('done', { requestId: 'req-1' })
        ])
      )
    )
    const deltas: Array<{ phase: string; content: string }> = []

    await streamChat(1, '问题', { onDelta: (delta) => deltas.push(delta) })

    expect(deltas).toEqual([
      { phase: 'reasoning', content: '先想' },
      { phase: 'answer', content: '答案' }
    ])
  })

  it('忽略与 meta 归属不一致的旧流增量', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          frame('meta', { sessionId: 1, requestId: 'req-1' }),
          frame('delta', { content: '旧流', phase: 'answer', requestId: 'req-2' }),
          frame('delta', { content: '新流', phase: 'answer', requestId: 'req-1' }),
          frame('done', { requestId: 'req-1' })
        ])
      )
    )
    const deltas: Array<{ phase: string; content: string }> = []

    await streamChat(1, '你好', { onDelta: (delta) => deltas.push(delta) })

    expect(deltas).toEqual([{ phase: 'answer', content: '新流' }])
  })

  it('重试请求在请求体中携带 retry=true', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      streamResponse([frame('done', { requestId: 'req-1' })])
    )
    vi.stubGlobal('fetch', fetchMock)

    await streamChat(1, '原问题', { onDelta: () => {} }, undefined, true)

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(JSON.parse(String(init.body))).toEqual({
      content: '原问题',
      retry: true
    })
  })

  it('HTTP 非 2xx 时抛出带状态码的错误', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('bad', { status: 502 })))

    await expect(streamChat(1, '你好', { onDelta: () => {} })).rejects.toThrow('HTTP 502')
  })

  it('error 事件通知 onError 后仍以业务错误拒绝 Promise', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([frame('error', { message: '模型不可用', code: 'UPSTREAM_ERROR' })])
      )
    )
    const onError = vi.fn()

    await expect(
      streamChat(1, '你好', { onDelta: () => {}, onError })
    ).rejects.toThrow('模型不可用')

    expect(onError).toHaveBeenCalledTimes(1)
    expect((onError.mock.calls[0][0] as Error & { code?: string }).code).toBe(
      'UPSTREAM_ERROR'
    )
  })

  it('onError 监听器自身抛错时不得覆盖原始流错误', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([frame('error', { message: '模型不可用' })])
      )
    )
    const onError = vi.fn(() => {
      throw new Error('监听器异常')
    })

    await expect(
      streamChat(1, '你好', { onDelta: () => {}, onError })
    ).rejects.toThrow('模型不可用')
  })

  it('无法解析的 payload 报「无法解析 SSE 响应」', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => streamResponse(['event: delta\ndata: 不是 JSON\n\n']))
    )
    const onError = vi.fn()

    await expect(
      streamChat(1, '你好', { onDelta: () => {}, onError })
    ).rejects.toThrow('无法解析 SSE 响应')
    expect(onError).toHaveBeenCalledTimes(1)
  })

  it('未收到 done 就断开时报连接意外结束', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => streamResponse([frame('meta', { sessionId: 1, requestId: 'req-1' })]))
    )

    await expect(
      streamChat(1, '你好', { onDelta: () => {} })
    ).rejects.toThrow('SSE 连接意外结束')
  })

  it('主动取消（AbortError）时静默结束', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => failingStreamResponse(new DOMException('aborted', 'AbortError')))
    )
    const onError = vi.fn()

    await expect(
      streamChat(1, '你好', { onDelta: () => {}, onError })
    ).resolves.toBeUndefined()
    expect(onError).not.toHaveBeenCalled()
  })

  it('8 秒内未收到任何响应首块时中止并提示重试', async () => {
    vi.useFakeTimers()
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
        const requestSignal = init?.signal as AbortSignal
        const body = new ReadableStream<Uint8Array>({
          start(controller) {
            requestSignal.addEventListener(
              'abort',
              () => controller.error(new DOMException('aborted', 'AbortError')),
              { once: true }
            )
          }
        })
        return new Response(body, { status: 200 })
      })
    )

    const stream = streamChat(1, '你好', { onDelta: () => {} })
    const assertion = expect(stream).rejects.toThrow('SSE 首包超时，请重试')
    await vi.advanceTimersByTimeAsync(8000)

    await assertion
    vi.useRealTimers()
  })
})
