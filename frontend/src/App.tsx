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

  // 장외 대비: 초기 진입 시 모의 분봉 시드 주입
  useEffect(() => {
    fetch('/api/mock/candles?symbol=005930&count=120')
      .then((res) => res.ok ? res.json() : Promise.reject(res))
      .then((json) => {
        const arr = Array.isArray(json?.data) ? json.data : []
        const mapped: LineData<Time>[] = arr.map((c: any) => ({ time: Number(c.time) as Time, value: Number(c.close) }))
        setSeed(mapped)
      })
      .catch(() => {
        // 백엔드 준비 전, 프론트에서 간단 시뮬레이션 시드 생성
        const now = Math.floor(Date.now() / 1000)
        const base = 70000
        const mock: LineData<Time>[] = Array.from({ length: 120 }).map((_, idx) => {
          const t = now - (120 - idx) * 60
          const v = base + Math.sin(idx / 5) * 200 + (Math.random() - 0.5) * 80
          return { time: t as Time, value: Math.round(v) }
        })
        setSeed(mock)
      })
  }, [])

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 16 }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ margin: 0 }}>실시간 차트</h2>
        <span style={{ fontSize: 12, opacity: 0.7 }}>{connected ? '연결됨' : '연결 중...'}</span>
      </header>
      <div style={{ marginTop: 12 }}>
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
