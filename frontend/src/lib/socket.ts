import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { tokenManager } from './tokenManager'

export type StompMessageHandler = (data: any, raw: string) => void

export function createStompClient(onMessage: StompMessageHandler, onConnect?: (frame: any) => void) {
  // 토큰 가져오기
  const token = tokenManager.getAccessToken()

  // 토큰이 유효한 경우에만 헤더 구성
  const connectHeaders: Record<string, string> = {}
  if (token && token !== 'null' && token !== 'undefined') {
    const authHeader = token.startsWith('Bearer ') ? token : `Bearer ${token}`
    connectHeaders['Authorization'] = authHeader
  }

  const apiBase = import.meta.env.VITE_API_BASE_URL || ''
  const wsUrl = apiBase ? `${apiBase}/ws` : '/ws'

  console.log('[STOMP] Connecting to:', wsUrl)

  const client = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    reconnectDelay: 3000,
    debug: (str) => console.log('[STOMP Debug]', str),
    connectHeaders: connectHeaders,
    onConnect: (frame) => {
      console.log('[STOMP] Connected!')
      
      const handle = (msg: IMessage) => {
        const raw = msg.body
        try {
          const json = JSON.parse(raw)
          onMessage(json, raw)
        } catch {
          onMessage(parseCaretPayload(raw), raw)
        }
      }

      // 기본 토픽 구독
      client.subscribe('/topic/stocks', handle)
      client.subscribe('/topic/stock/updates', handle)
      
      // 추가 연결 콜백이 있으면 실행
      if (onConnect) {
        onConnect(frame)
      }
    },
    onStompError: (frame) => {
      console.error('[STOMP Error]', frame)
    },
    onDisconnect: () => {
      console.warn('[STOMP] Disconnected')
    }
  })

  return client
}

function parseCaretPayload(raw: string) {
  try {
    const parts = raw.split('|')
    const data = parts[3] ?? ''
    const fields = data.split('^')
    return {
      ticker: fields[0],
      tradeTime: fields[1],
      //       curTime: fields[1],
      price: fields[2],
      volume: fields[8] ?? undefined,
      accumulatedVolume: fields[9] ?? undefined,
    }
  } catch {
    return { raw }
  }
}



