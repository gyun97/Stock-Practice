import { useEffect, useMemo, useRef, useState } from 'react'
import './App.css'
import { createStompClient } from './lib/socket'
import LiveChart from './components/LiveChart'
import type { LineData, Time } from 'lightweight-charts'

type Tick = { price?: number; tradeTime?: string; stockCode?: string }

export default function App() {
  const [last, setLast] = useState<Tick>({})
  const [connected, setConnected] = useState(false)
  const [seed, setSeed] = useState<LineData<Time>[]>([])
  const clientRef = useRef<ReturnType<typeof createStompClient> | null>(null)

  const handler = useMemo(() => (payload: any) => {
    const price = toNum(payload?.price ?? payload?.currentPrice)
    const tradeTime = String(payload?.tradeTime ?? '')
    const stockCode = String(payload?.stockCode ?? payload?.symbol ?? '')
    if (price != null) setLast({ price, tradeTime, stockCode })
  }, [])

  useEffect(() => {
    const client = createStompClient((data) => handler(data))
    client.onConnect = () => setConnected(true)
    client.onStompError = () => setConnected(false)
    client.onWebSocketClose = () => setConnected(false)
    client.activate()
    clientRef.current = client
    return () => { client.deactivate() }
  }, [handler])

  // 주식 구독 시작 및 초기 데이터 로드
  useEffect(() => {
    // 백엔드에 삼성전자 주식 구독 요청
    fetch('/api/stocks/H0STCNT0/005930')
      .then((res) => res.ok ? res.json() : Promise.reject(res))
      .then((json) => {
        console.log('주식 구독 성공:', json)
      })
      .catch((err) => {
        console.log('주식 구독 실패:', err)
      })

    // 초기 차트용 모의 데이터 (실제 데이터가 올 때까지)
    const now = Math.floor(Date.now() / 1000)
    const base = 70000
    const mock: LineData<Time>[] = Array.from({ length: 60 }).map((_, idx) => {
      const t = now - (60 - idx) * 60
      const v = base + Math.sin(idx / 10) * 100 + (Math.random() - 0.5) * 50
      return { time: t as Time, value: Math.round(v) }
    })
    setSeed(mock)
  }, [])

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: 16 }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 24, fontWeight: 'bold' }}>삼성전자 (005930)</h1>
          <p style={{ margin: '4px 0 0 0', color: '#666', fontSize: 14 }}>
            {last.price ? `현재가: ${last.price.toLocaleString()}원` : '실시간 가격 대기 중...'}
          </p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ 
            padding: '4px 12px', 
            borderRadius: 16, 
            fontSize: 12, 
            fontWeight: 'bold',
            backgroundColor: connected ? '#10b981' : '#ef4444',
            color: 'white'
          }}>
            {connected ? '실시간 연결됨' : '연결 중...'}
          </div>
          {last.tradeTime && (
            <div style={{ fontSize: 11, color: '#666', marginTop: 4 }}>
              마지막 업데이트: {last.tradeTime}
            </div>
          )}
        </div>
      </header>
      
      <div style={{ 
        border: '1px solid #e5e7eb', 
        borderRadius: 8, 
        padding: 16,
        backgroundColor: '#fafafa'
      }}>
        <LiveChart lastPrice={last.price} lastTime={last.tradeTime} seed={seed} />
      </div>
    </div>
  )
}

function toNum(v: any): number | undefined {
  if (v == null) return undefined
  const n = Number(String(v).replace(/[^0-9.-]/g, ''))
  return Number.isFinite(n) ? n : undefined
}
