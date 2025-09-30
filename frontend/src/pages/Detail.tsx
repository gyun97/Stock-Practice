import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import LiveChart from '../components/LiveChart'
import { createStompClient } from '../lib/socket'

export default function Detail() {
  const { ticker = '' } = useParams()
  const [lastPrice, setLastPrice] = useState<number | undefined>(undefined)
  const [lastTime, setLastTime] = useState<string | undefined>(undefined)
  const stompRef = useRef<ReturnType<typeof createStompClient> | null>(null)

  const onTick = useMemo(() => (payload: any) => {
    const code = String(payload?.stockCode ?? payload?.symbol ?? '')
    if (code !== ticker) return
    const price = toNum(payload?.price ?? payload?.stck_prpr)
    const tradeTime = String(payload?.tradeTime ?? payload?.stck_cntg_hour ?? '')
    if (price == null) return
    setLastPrice(price)
    setLastTime(tradeTime)
  }, [ticker])

  useEffect(() => {
    const client = createStompClient(onTick)
    client.activate()
    stompRef.current = client
    return () => { client.deactivate() }
  }, [onTick])

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <Link to="/" style={{ textDecoration: 'none' }}>← 목록</Link>
        <h2 style={{ margin: 0 }}>{ticker}</h2>
        {lastPrice != null && <span style={{ color: '#6b7280' }}>{lastPrice.toLocaleString()}원</span>}
      </div>
      <LiveChart lastPrice={lastPrice} lastTime={lastTime} />
    </div>
  )
}

function toNum(v: any): number | undefined {
  if (v == null) return undefined
  const n = Number(String(v).replace(/[^0-9.-]/g, ''))
  return Number.isFinite(n) ? n : undefined
}


