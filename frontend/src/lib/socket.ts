import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { tokenManager } from './tokenManager'

export type StompMessageHandler = (data: any, raw: string) => void

export function createStompClient(onMessage: StompMessageHandler) {
  // 토큰 가져오기
  const token = tokenManager.getAccessToken()

  // 토큰이 유효한 경우에만 헤더 구성
  const connectHeaders: Record<string, string> = {}
  if (token && token !== 'null' && token !== 'undefined') {
    const authHeader = token.startsWith('Bearer ') ? token : `Bearer ${token}`
    connectHeaders['Authorization'] = authHeader
  }

  const client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 3000,
    debug: (str) => console.log(str),
    connectHeaders: connectHeaders
  })
  client.onConnect = () => {
    const handle = (msg: IMessage) => {
      const raw = msg.body
      try {
        const json = JSON.parse(raw)
        onMessage(json, raw)
      } catch {
        onMessage(parseCaretPayload(raw), raw)
      }
    }
    // 기본 토픽
    client.subscribe('/topic/stocks', handle)
    // Redis bridge 전용 토픽도 함께 시도 (백엔드 설정에 따라 미등록이면 무시)
    client.subscribe('/topic/stock/updates', handle)
  }
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



