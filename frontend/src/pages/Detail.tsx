import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { createStompClient } from '../lib/socket'

export default function Detail() {
  const { ticker = '' } = useParams()
  const [lastPrice, setLastPrice] = useState<number | undefined>(undefined)
  const [lastTime, setLastTime] = useState<string | undefined>(undefined)
  const stompRef = useRef<ReturnType<typeof createStompClient> | null>(null)

  const onTick = useMemo(() => (payload: any) => {
    const code = String(payload?.ticker ?? payload?.symbol ?? '')
    if (code !== ticker) return
    const price = toNum(payload?.price ?? payload?.stck_prpr)
    const tradeTime = String(payload?.tradeTime ?? payload?.stck_cntg_hour ?? '')
    if (price == null) return
    
    // 장 마감 시간(15:30) 이후에는 실시간 업데이트 중단
    if (!isMarketOpen()) return
    
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
      
      <div style={{ marginBottom: 16 }}>
        <Link 
          to={`/stocks/${ticker}/chart`} 
          style={{
            display: 'inline-block',
            padding: '12px 24px',
            background: '#2962FF',
            color: 'white',
            textDecoration: 'none',
            borderRadius: 6,
            fontWeight: 'bold'
          }}
        >
          📈 차트 보기
        </Link>
      </div>
      
      <div style={{ padding: 20, border: '1px solid #ddd', borderRadius: 8, background: '#f9f9f9' }}>
        <p>실시간 가격 정보가 여기에 표시됩니다.</p>
        {lastPrice != null && (
          <div>
            <p><strong>현재가:</strong> {lastPrice.toLocaleString()}원</p>
            {lastTime && <p><strong>거래시간:</strong> {lastTime}</p>}
          </div>
        )}
      </div>
    </div>
  )
}

function toNum(v: any): number | undefined {
  if (v == null) return undefined
  const n = Number(String(v).replace(/[^0-9.-]/g, ''))
  return Number.isFinite(n) ? n : undefined
}

// 장 마감 시간 체크 (한국 시간 기준)
function isMarketOpen(): boolean {
  const now = new Date()
  const kst = new Date(now.getTime() + (9 * 60 * 60 * 1000)) // UTC+9
  const dayOfWeek = kst.getDay()
  const hours = kst.getHours()
  const minutes = kst.getMinutes()
  
  // 주말 체크
  if (dayOfWeek === 0 || dayOfWeek === 6) return false
  
  // 장 시간 체크 (09:00 ~ 15:30)
  const currentTime = hours * 60 + minutes
  const marketOpen = 9 * 60 // 09:00
  const marketClose = 15 * 60 + 30 // 15:30
  
  return currentTime >= marketOpen && currentTime <= marketClose
}



