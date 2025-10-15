import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { createStompClient } from '../lib/socket'

export default function Detail() {
  const { ticker = '' } = useParams()
  const [lastPrice, setLastPrice] = useState<number | undefined>(undefined)
  const [lastTime, setLastTime] = useState<string | undefined>(undefined)
  const [companyName, setCompanyName] = useState<string>('')
  const [logoUrl, setLogoUrl] = useState<string | undefined>(undefined)
  const [changeAmount, setChangeAmount] = useState<number | undefined>(undefined)
  const [changeRate, setChangeRate] = useState<number | undefined>(undefined)
  const [volume, setVolume] = useState<number | undefined>(undefined)
  const stompRef = useRef<ReturnType<typeof createStompClient> | null>(null)

  // 회사 정보 및 초기 주식 데이터 로드 함수
  const loadCompanyInfo = async () => {
    if (!ticker) return
    
    try {
      const response = await fetch(`/api/v1/stocks`)
      if (response.ok) {
        const result = await response.json()
        const stocks = result.data
        if (stocks && Array.isArray(stocks)) {
          const stock = stocks.find(s => s.ticker === ticker)
          if (stock) {
            if (stock.companyName) {
              setCompanyName(stock.companyName)
              // 로고 URL을 기업명으로 직접 생성
              setLogoUrl(`/logos/${stock.companyName}.png`)
            }
            
            // 초기 주식 데이터 설정
            if (stock.price != null) setLastPrice(stock.price)
            if (stock.changeAmount != null) setChangeAmount(stock.changeAmount)
            if (stock.changeRate != null) setChangeRate(stock.changeRate)
            if (stock.volume != null) setVolume(stock.volume)
            if (stock.tradeTime) setLastTime(stock.tradeTime)
          }
        }
      }
    } catch (error) {
      console.error('회사 정보 로드 실패:', error)
    }
  }

  const onTick = useMemo(() => (payload: any, raw: string) => {
    console.log('Detail onTick 호출됨:', { payload, raw, ticker })
    
    const code = String(payload?.ticker ?? payload?.symbol ?? '')
    console.log('수신된 ticker:', code, '현재 ticker:', ticker)
    
    if (code !== ticker) {
      console.log('ticker 불일치로 무시:', code, '!==', ticker)
      return
    }
    
    const price = toNum(payload?.price ?? payload?.stck_prpr)
    const tradeTime = String(payload?.tradeTime ?? payload?.stck_cntg_hour ?? '')
    const changeAmountValue = toNum(payload?.changeAmount ?? payload?.prdy_vrss)
    const changeRateValue = toNum(payload?.changeRate ?? payload?.prdy_ctrt)
    const volumeValue = toNum(payload?.volume ?? payload?.acml_vol ?? payload?.accumulatedVolume)
    
    console.log('파싱된 데이터:', { price, tradeTime, changeAmountValue, changeRateValue, volumeValue })
    
    if (price == null) {
      console.log('price가 null이어서 무시')
      return
    }
    
    // 장 마감 시간 체크를 주석 처리하여 항상 실시간 업데이트 허용
    // if (!isMarketOpen()) return
    
    console.log('실시간 업데이트 적용:', { ticker, price, changeAmountValue, changeRateValue, volumeValue, tradeTime })
    
    setLastPrice(price)
    setLastTime(tradeTime)
    if (changeAmountValue != null) setChangeAmount(changeAmountValue)
    if (changeRateValue != null) setChangeRate(changeRateValue)
    if (volumeValue != null) setVolume(volumeValue)
  }, [ticker])

  useEffect(() => {
    console.log('Detail 페이지 마운트, ticker:', ticker)
    loadCompanyInfo()
    
    const client = createStompClient(onTick)
    console.log('WebSocket 클라이언트 생성:', client)
    
    // WebSocket 연결 상태 확인을 위한 추가 로그
    client.onConnect = () => {
      console.log('Detail 페이지 WebSocket 연결 성공!')
    }
    
    client.onStompError = (frame) => {
      console.error('Detail 페이지 WebSocket STOMP 오류:', frame)
    }
    
    client.activate()
    stompRef.current = client
    
    return () => { 
      console.log('Detail 페이지 언마운트, WebSocket 연결 해제')
      client.deactivate() 
    }
  }, [onTick])


  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <Link to="/" style={{ textDecoration: 'none' }}>← 목록</Link>
        <LogoCell name={companyName || ticker} ticker={ticker} logoUrl={logoUrl} />
        <h2 style={{ margin: 0 }}>{companyName || ticker}</h2>
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
        <h3 style={{ marginTop: 0 }}>실시간 주식 정보</h3>
        {lastPrice != null ? (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div>
              <div style={{ fontSize: 14, color: '#666', marginBottom: 4 }}>현재가</div>
              <div style={{ 
                fontSize: 24, 
                fontWeight: 'bold', 
                color: changeAmount != null && changeAmount >= 0 ? '#e74c3c' : '#3498db'
              }}>
                {lastPrice.toLocaleString()}원
              </div>
            </div>
            
            <div>
              <div style={{ fontSize: 14, color: '#666', marginBottom: 4 }}>등락률</div>
              <div style={{ 
                fontSize: 18, 
                fontWeight: 'bold',
                color: changeAmount != null && changeAmount >= 0 ? '#e74c3c' : '#3498db'
              }}>
                {changeAmount != null ? (
                  <>
                    {changeAmount >= 0 ? '+' : ''}{changeAmount.toLocaleString()}원
                    {changeRate != null && (
                      <span style={{ fontSize: 14, marginLeft: 8 }}>
                        ({changeRate >= 0 ? '+' : ''}{changeRate.toFixed(2)}%)
                      </span>
                    )}
                  </>
                ) : '-'}
              </div>
            </div>
            
            <div>
              <div style={{ fontSize: 14, color: '#666', marginBottom: 4 }}>거래량</div>
              <div style={{ fontSize: 16, fontWeight: 'bold', color: '#333' }}>
                {volume != null ? `${Math.round(volume / 1000000)}M` : '-'}
              </div>
            </div>
            
            <div>
              <div style={{ fontSize: 14, color: '#666', marginBottom: 4 }}>체결시간</div>
              <div style={{ fontSize: 14, color: '#333' }}>
                {lastTime || '실시간'}
              </div>
            </div>
          </div>
        ) : (
          <div style={{ textAlign: 'center', color: '#666' }}>
            <p>주식 데이터를 불러오는 중...</p>
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

function LogoCell({ name, ticker, logoUrl }: { name: string; ticker: string; logoUrl?: string }) {
  const fallbackBg = '#e5e7eb'
  const initials = (name || ticker || '?').slice(0, 2)
  
  // 로고 URL 우선 사용, 없으면 기업명으로 생성
  const src = logoUrl || `/logos/${name || ticker}.png`
  
  console.log('로고 로딩 시도:', { name, ticker, logoUrl, src })
  
  const onError = (e: any) => {
    console.log('로고 로딩 실패:', src)
    e.currentTarget.style.display = 'none'
    const sib = e.currentTarget.nextSibling as HTMLElement | null
    if (sib) sib.style.display = 'flex'
  }
  
  return (
    <div style={{ display: 'flex', alignItems: 'center' }}>
      <img 
        src={src} 
        alt={name} 
        width={40} 
        height={40} 
        style={{ 
          borderRadius: 8, 
          objectFit: 'contain', 
          background: '#fff', 
          border: '1px solid #e5e7eb' 
        }} 
        onError={onError}
        onLoad={() => console.log('로고 로딩 성공:', src)}
      />
      <div style={{ 
        display: 'none', 
        width: 40, 
        height: 40, 
        borderRadius: 8, 
        alignItems: 'center', 
        justifyContent: 'center', 
        background: fallbackBg, 
        color: '#374151', 
        fontWeight: 600, 
        fontSize: 14, 
        border: '1px solid #e5e7eb' 
      }}>
        {initials}
      </div>
    </div>
  )
}
