import { useEffect, useState, useRef, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import { createStompClient } from '../lib/socket'

export default function Chart() {
  const { ticker = '' } = useParams()
  const [candleData, setCandleData] = useState([])
  const [selectedPeriod, setSelectedPeriod] = useState('MIN')
  const [loading, setLoading] = useState(false)
  const [companyName, setCompanyName] = useState('')
  const [currentStockId, setCurrentStockId] = useState(null)
  const [currentPrice, setCurrentPrice] = useState(null)
  const [orderTab, setOrderTab] = useState('market') // 'market' | 'reserve'
  const [qty, setQty] = useState(1)
  const [reserveQty, setReserveQty] = useState(1)
  const [reservePrice, setReservePrice] = useState('')
  const [placing, setPlacing] = useState(false)
  const [orderMsg, setOrderMsg] = useState('')
  const [orderErr, setOrderErr] = useState('')
  const canvasRef = useRef(null)
  const stompRef = useRef(null)
  const tickerRef = useRef(ticker)

  // ticker 변경 시 ref 업데이트
  useEffect(() => {
    tickerRef.current = ticker
  }, [ticker])

  const periods = [
    { key: 'MIN', label: '분' },
    { key: 'D', label: '일' },
    { key: 'W', label: '주' },
    { key: 'M', label: '월' },
    { key: 'Y', label: '년' }
  ]

  // 회사 정보 및 실시간 시세 로드 함수
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
            }
            if (stock.stockId || stock.id) {
              setCurrentStockId(stock.stockId ?? stock.id)
            }
            // 실시간 시세 정보 설정
            setCurrentPrice({
              price: stock.price,
              changeAmount: stock.changeAmount,
              changeRate: stock.changeRate,
              volume: stock.volume,
              tradeTime: stock.tradeTime
            })
          }
        }
      }
    } catch (error) {
      console.error('회사 정보 로드 실패:', error)
    }
  }

  // 실시간 업데이트 핸들러 (메인 페이지와 동일한 방식)
  const onTick = useMemo(() => (payload, raw) => {
    const currentTicker = tickerRef.current
    console.log('Chart onTick 호출됨:', { payload, raw, currentTicker })

    const code = String(payload?.ticker ?? payload?.symbol ?? '')
    console.log('수신된 ticker:', code, '현재 ticker:', currentTicker)

    if (code !== currentTicker) {
      console.log('ticker 불일치로 무시:', code, '!==', currentTicker)
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

    console.log('실시간 업데이트 적용:', { ticker: currentTicker, price, changeAmountValue, changeRateValue, volumeValue, tradeTime })

    // 실시간 시세 정보 업데이트
    setCurrentPrice({
      price: price,
      changeAmount: changeAmountValue || 0,
      changeRate: changeRateValue || 0,
      volume: volumeValue || 0,
      tradeTime: tradeTime
    })
  }, [])

  // 숫자 변환 유틸리티 함수
  function toNum(v) {
    if (v == null) return undefined
    const n = Number(String(v).replace(/[^0-9.-]/g, ''))
    return Number.isFinite(n) ? n : undefined
  }

  // 데이터 로드 함수
  const loadData = async (period) => {
    if (!ticker) return

    setLoading(true)
    try {
      console.log(`데이터 로드 시작: ${ticker}, period: ${period}`)

      let response
      if (period === 'MIN') {
        // 분 단위 데이터는 다른 API 사용
        response = await fetch(`/api/v1/stocks/${ticker}`)
      } else {
        // 기간별 데이터는 기존 API 사용
        response = await fetch(`/api/v1/stocks/${ticker}/period?period=${period}`)
      }

      console.log('응답 상태:', response.status)

      if (!response.ok) {
        throw new Error(`데이터 로드 실패: ${response.status}`)
      }

      const result = await response.json()
      console.log('받은 데이터:', result)

      const data = result.data || []
      console.log('파싱된 캔들 데이터:', data.slice(0, 3))

      if (data.length > 0) {
        // 분 단위는 시간순 정렬, 나머지는 날짜순 정렬
        const sortedData = period === 'MIN'
          ? [...data].sort((a, b) => {
              // 분 단위는 date+time으로 정렬
              const aDateTime = a.date + (a.time || '000000')
              const bDateTime = b.date + (b.time || '000000')
              return aDateTime.localeCompare(bDateTime)
            })
          : [...data].sort((a, b) => a.date.localeCompare(b.date))

        console.log('정렬된 데이터:', sortedData.slice(0, 3))

        // 정렬된 데이터로 상태 업데이트 및 차트 그리기
        setCandleData(sortedData)
        drawChart(sortedData, period)
      } else {
        setCandleData([])
      }
    } catch (error) {
      console.error('데이터 로드 실패:', error)
    } finally {
      setLoading(false)
    }
  }

  const getToken = () => localStorage.getItem('accessToken')
  const ensureLogin = () => {
    const t = getToken()
    if (!t) {
      setOrderErr('로그인이 필요합니다.')
      return false
    }
    return true
  }

  const placeMarket = async (type) => {
    if (!ensureLogin()) return
    if (!ticker) return
    if (!qty || qty <= 0) { setOrderErr('수량을 입력하세요.'); return }
    setPlacing(true); setOrderErr(''); setOrderMsg('')
    try {
      const url = type === 'buy'
        ? `/api/v1/orders/buying/${ticker}?quantity=${qty}`
        : `/api/v1/orders/selling/${ticker}?quantity=${qty}`
      const res = await fetch(url, { method: 'POST', headers: { 'Authorization': `Bearer ${getToken()}` } })
      const json = await res.json().catch(() => null)
      if (res.ok) {
        setOrderMsg(json?.data ?? '주문이 접수되었습니다.')
      } else {
        setOrderErr(json?.message ?? `주문 실패 (${res.status})`)
      }
    } catch (e) {
      setOrderErr('네트워크 오류가 발생했습니다.')
    } finally { setPlacing(false) }
  }

  const placeReserve = async (type) => {
    if (!ensureLogin()) return
    if (!ticker) return
    const q = Number(reserveQty)
    const p = Number(String(reservePrice).replace(/[^0-9]/g, ''))
    if (!q || q <= 0) { setOrderErr('수량을 입력하세요.'); return }
    if (!p || p <= 0) { setOrderErr('예약 가격을 입력하세요.'); return }
    setPlacing(true); setOrderErr(''); setOrderMsg('')
    try {
      const path = type === 'buy' ? 'reserve-buying' : 'reserve-selling'
      const url = `/api/v1/orders/${path}/${ticker}?quantity=${q}&targetPrice=${p}`
      const res = await fetch(url, { method: 'POST', headers: { 'Authorization': `Bearer ${getToken()}` } })
      const json = await res.json().catch(() => null)
      if (res.ok) {
        setOrderMsg(json?.data ?? '예약 주문이 등록되었습니다.')
      } else {
        setOrderErr(json?.message ?? `예약 주문 실패 (${res.status})`)
      }
    } catch (e) {
      setOrderErr('네트워크 오류가 발생했습니다.')
    } finally { setPlacing(false) }
  }

  const [myOrders, setMyOrders] = useState([])
  const loadMyOrders = async () => {
    const token = getToken()
    if (!token) return
    try {
      const res = await fetch('/api/v1/orders', { headers: { 'Authorization': `Bearer ${token}` } })
      if (res.ok) {
        const json = await res.json()
        const list = Array.isArray(json?.data) ? json.data : []
        setMyOrders(list)
      }
    } catch (e) {
      // ignore
    }
  }

  const cancelReservation = async (orderId) => {
    const token = getToken()
    if (!token) { setOrderErr('로그인이 필요합니다.'); return }
    setPlacing(true); setOrderErr(''); setOrderMsg('')
    try {
      const res = await fetch(`/api/v1/orders/${orderId}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${token}` } })
      const json = await res.json().catch(() => null)
      if (res.ok) {
        setOrderMsg('예약 주문이 취소되었습니다.')
        // 목록 갱신
        loadMyOrders()
      } else {
        setOrderErr(json?.message ?? `취소 실패 (${res.status})`)
      }
    } catch (e) {
      setOrderErr('네트워크 오류가 발생했습니다.')
    } finally { setPlacing(false) }
  }

  // 분리된 차트 그리기 (주가 라인 + 거래량 막대)
  const drawChart = (data, period = 'D') => {
    const canvas = canvasRef.current
    if (!canvas || data.length === 0) return

    const ctx = canvas.getContext('2d')
    const width = canvas.width
    const height = canvas.height

    // 캔버스 초기화
    ctx.clearRect(0, 0, width, height)

    // 좌표계 설정
    const marginLeft = 60
    const marginRight = 20
    const marginTop = 20
    const marginBottom = 60
    const chartWidth = width - marginLeft - marginRight
    const chartHeight = height - marginTop - marginBottom

    // 데이터 범위 계산
    const prices = data.map(d => d.close)
    const volumes = data.map(d => d.volume)
    const maxPrice = Math.max(...prices)
    const maxVolume = Math.max(...volumes)
    const minPrice = 0

    // 두 개의 분리된 차트 영역
    const priceChartHeight = chartHeight * 0.5  // 상단 50%
    const volumeChartHeight = chartHeight * 0.5  // 하단 50%
    const gapBetweenCharts = 20  // 차트 간 간격

    const priceRange = maxPrice - minPrice

    // 1. 주가 라인 차트 그리기 (상단)
    ctx.strokeStyle = '#2962FF'
    ctx.lineWidth = 2
    ctx.beginPath()

    data.forEach((item, index) => {
      const x = marginLeft + (index / (data.length - 1)) * chartWidth
      const y = marginTop + priceChartHeight - ((item.close - minPrice) / priceRange) * priceChartHeight

      if (index === 0) {
        ctx.moveTo(x, y)
      } else {
        ctx.lineTo(x, y)
      }
    })

    ctx.stroke()

    // 주가 라인에 점 표시
    data.forEach((item, index) => {
      const x = marginLeft + (index / (data.length - 1)) * chartWidth
      const y = marginTop + priceChartHeight - ((item.close - minPrice) / priceRange) * priceChartHeight

      ctx.fillStyle = '#2962FF'
      ctx.beginPath()
      ctx.arc(x, y, 3, 0, 2 * Math.PI)
      ctx.fill()
    })

    // 주가 Y축 라벨
    ctx.fillStyle = '#666'
    ctx.font = '12px Arial'
    ctx.textAlign = 'right'

    for (let i = 0; i <= 4; i++) {
      const value = maxPrice - (i / 4) * (maxPrice - 0)
      const y = marginTop + priceChartHeight - (i / 4) * priceChartHeight + 4
      ctx.fillText(Math.round(value).toLocaleString(), marginLeft - 10, y)
    }

    // 주가 제목
    ctx.fillStyle = '#333'
    ctx.font = 'bold 12px Arial'
    ctx.textAlign = 'left'
    ctx.fillText('주가', marginLeft, marginTop - 5)

    // 2. 거래량 막대 그래프 그리기 (하단)
    const volumeStartY = marginTop + priceChartHeight + gapBetweenCharts
    const barWidth = chartWidth / data.length * 0.8

    data.forEach((item, index) => {
      const x = marginLeft + (index / (data.length - 1)) * chartWidth - barWidth / 2
      const barHeight = (item.volume / maxVolume) * volumeChartHeight
      const y = volumeStartY + volumeChartHeight - barHeight

      // 막대 그래프 그리기 - 빨간색으로 통일
      const barColor = '#ff6b6b'
      ctx.fillStyle = barColor
      ctx.fillRect(x, y, barWidth, barHeight)

      // 거래량 수치 표시 (일부 날짜에만)
      if (index % Math.ceil(data.length / 8) === 0) {
        ctx.fillStyle = '#666'
        ctx.font = '10px Arial'
        ctx.textAlign = 'center'
        ctx.textBaseline = 'bottom'
        const volumeInMillion = Math.round(item.volume / 1000000)
        ctx.fillText(`${volumeInMillion}M`, x + barWidth / 2, y - 2)
      }
    })

    // 거래량 Y축 라벨
    ctx.fillStyle = '#666'
    ctx.font = '10px Arial'
    ctx.textAlign = 'right'

    for (let i = 0; i <= 4; i++) {
      const value = maxVolume - (i / 4) * maxVolume
      const y = volumeStartY + volumeChartHeight - (i / 4) * volumeChartHeight + 4
      ctx.fillText(Math.round(value).toLocaleString(), marginLeft - 10, y)
    }

    // 거래량 제목
    ctx.fillStyle = '#333'
    ctx.font = 'bold 12px Arial'
    ctx.textAlign = 'left'
    ctx.fillText('거래량', marginLeft, volumeStartY - 5)

    // X축 라벨 (일부 날짜/시간만)
    ctx.fillStyle = '#666'
    ctx.font = '12px Arial'
    ctx.textAlign = 'center'
    const labelCount = Math.min(data.length, 10)
    for (let i = 0; i < labelCount; i++) {
      const index = Math.floor((i / labelCount) * data.length)
      const x = marginLeft + (index / (data.length - 1)) * chartWidth
      const y = volumeStartY + volumeChartHeight + 20

      let labelText
      if (period === 'MIN') {
        // 분 단위는 시간 표시
        const timeStr = data[index].time || '000000'
        const hour = timeStr.substring(0, 2)
        const minute = timeStr.substring(2, 4)
        labelText = `${hour}:${minute}`
      } else {
        // 기간별은 날짜 표시
        const dateStr = data[index].date
        const year = dateStr.substring(0, 4)
        const month = dateStr.substring(4, 6)
        const day = dateStr.substring(6, 8)
        labelText = `${year}-${month}-${day}`
      }

      ctx.fillText(labelText, x, y)
    }

  }

  // WebSocket 연결 (메인 페이지와 동일한 방식)
  useEffect(() => {
    console.log('Chart 페이지 WebSocket 연결 시작')
    const client = createStompClient(onTick)
    client.activate()
    stompRef.current = client
    return () => { 
      console.log('Chart 페이지 WebSocket 연결 해제')
      client.deactivate() 
    }
  }, [onTick])

  // 기간 변경 시 데이터 다시 로드
  useEffect(() => {
    console.log('Chart 페이지 마운트, ticker:', ticker)
    loadCompanyInfo()
    loadData(selectedPeriod)
    loadMyOrders()
  }, [ticker, selectedPeriod])

  return (
    <div style={{ maxWidth: 1400, margin: '0 auto', padding: 16 }}>
      {/* 헤더 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Link to="/" style={{ textDecoration: 'none', color: '#666' }}>← 목록</Link>
          <h1 style={{ margin: 0, fontSize: 24 }}>{companyName || ticker}</h1>
        </div>

        {/* 기간 선택 버튼 - 더 왼쪽으로 이동 */}
        <div style={{ display: 'flex', gap: 8, marginRight: 200 }}>
          {periods.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setSelectedPeriod(key)}
              style={{
                padding: '8px 16px',
                border: '1px solid #ddd',
                borderRadius: 4,
                background: selectedPeriod === key ? '#2962FF' : 'white',
                color: selectedPeriod === key ? 'white' : '#333',
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: selectedPeriod === key ? 'bold' : 'normal',
              }}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* 로딩 상태 */}
      {loading && (
        <div style={{ textAlign: 'center', padding: 40, color: '#666' }}>
          데이터를 불러오는 중...
        </div>
      )}

      {/* 실시간 시세 정보 */}
      {currentPrice && (
        <div style={{
          background: '#f8f9fa',
          border: '1px solid #e9ecef',
          borderRadius: 8,
          padding: '16px 20px',
          marginBottom: 16,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          maxWidth: 1200
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
            {/* 현재가 */}
            <div>
              <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>현재가</div>
              <div style={{
                fontSize: 24,
                fontWeight: 'bold',
                color: currentPrice.changeAmount >= 0 ? '#e74c3c' : '#3498db'
              }}>
                {currentPrice.price.toLocaleString()}원
              </div>
            </div>

            {/* 등락률 */}
            <div>
              <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>등락률</div>
              <div style={{
                fontSize: 18,
                fontWeight: 'bold',
                color: currentPrice.changeAmount >= 0 ? '#e74c3c' : '#3498db'
              }}>
                {currentPrice.changeAmount >= 0 ? '+' : ''}{currentPrice.changeAmount.toLocaleString()}원
                <span style={{ fontSize: 14, marginLeft: 8 }}>
                  ({currentPrice.changeAmount >= 0 ? '+' : ''}{currentPrice.changeRate.toFixed(2)}%)
                </span>
              </div>
            </div>

            {/* 거래량 */}
            <div>
              <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>거래량</div>
              <div style={{ fontSize: 16, fontWeight: 'bold', color: '#333' }}>
                {Math.round(currentPrice.volume / 1000000)}M
              </div>
            </div>
          </div>

          {/* 체결시간 */}
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>체결시간</div>
            <div style={{ fontSize: 14, color: '#333' }}>
              {currentPrice.tradeTime || '실시간'}
            </div>
          </div>
        </div>
      )}

      {/* 차트 + 우측 주문패널 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 16, alignItems: 'start' }}>
        <div>
          <canvas
            ref={canvasRef}
            width={1200}
            height={800}
            style={{
              border: '1px solid #ddd',
              borderRadius: 8,
              marginBottom: 20,
              background: 'white',
              width: '100%',
              maxWidth: 1200
            }}
          />
        </div>

        <div style={{ border: '1px solid #e5e7eb', borderRadius: 8, background: '#ffffff', padding: 16, position: 'sticky', top: 16 }}>
          <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
            <button onClick={() => setOrderTab('market')} style={{ flex: 1, padding: '8px 0', borderRadius: 6, border: '1px solid #d1d5db', background: orderTab==='market' ? '#2962FF' : 'white', color: orderTab==='market' ? 'white' : '#111827', fontWeight: 600 }}>즉시 주문</button>
            <button onClick={() => setOrderTab('reserve')} style={{ flex: 1, padding: '8px 0', borderRadius: 6, border: '1px solid #d1d5db', background: orderTab==='reserve' ? '#2962FF' : 'white', color: orderTab==='reserve' ? 'white' : '#111827', fontWeight: 600 }}>예약 주문</button>
          </div>

          {orderMsg && (
            <div style={{ marginBottom: 10, padding: 10, borderRadius: 6, background: '#ecfdf5', border: '1px solid #a7f3d0', color: '#065f46', fontSize: 13 }}>{orderMsg}</div>
          )}
          {orderErr && (
            <div style={{ marginBottom: 10, padding: 10, borderRadius: 6, background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', fontSize: 13 }}>{orderErr}</div>
          )}

          {orderTab === 'market' ? (
            <div>
              <div style={{ marginBottom: 10 }}>
                <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 6 }}>수량</div>
                <input value={qty} onChange={e => setQty(Number(e.target.value.replace(/[^0-9]/g, '')) || 0)} inputMode="numeric" pattern="[0-9]*" placeholder="주문 수량" style={{ width: '100%', padding: '10px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 14 }} />
                <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
                  {[1,5,10,50,100].map(n => (
                    <button key={n} onClick={() => setQty(n)} style={{ flex: 1, padding: '6px 0', border: '1px solid #e5e7eb', borderRadius: 6, background: 'white', cursor: 'pointer', fontSize: 12 }}>{n}주</button>
                  ))}
                </div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                <button disabled={placing} onClick={() => placeMarket('buy')} style={{ padding: '10px 12px', borderRadius: 6, border: '1px solid #16a34a', background: '#16a34a', color: 'white', fontWeight: 600, cursor: placing?'not-allowed':'pointer' }}>{placing ? '처리중...' : '즉시 매수'}</button>
                <button disabled={placing} onClick={() => placeMarket('sell')} style={{ padding: '10px 12px', borderRadius: 6, border: '1px solid #dc2626', background: '#dc2626', color: 'white', fontWeight: 600, cursor: placing?'not-allowed':'pointer' }}>{placing ? '처리중...' : '즉시 매도'}</button>
              </div>
            </div>
          ) : (
            <div>
              <div style={{ marginBottom: 10 }}>
                <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 6 }}>수량</div>
                <input value={reserveQty} onChange={e => setReserveQty(Number(e.target.value.replace(/[^0-9]/g, '')) || 0)} inputMode="numeric" pattern="[0-9]*" placeholder="주문 수량" style={{ width: '100%', padding: '10px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 14 }} />
              </div>
              <div style={{ marginBottom: 12 }}>
                <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 6 }}>예약 가격(원)</div>
                <input value={reservePrice} onChange={e => setReservePrice(e.target.value.replace(/[^0-9]/g, ''))} inputMode="numeric" pattern="[0-9]*" placeholder="예: 95000" style={{ width: '100%', padding: '10px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 14 }} />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                <button disabled={placing} onClick={() => placeReserve('buy')} style={{ padding: '10px 12px', borderRadius: 6, border: '1px solid #16a34a', background: '#16a34a', color: 'white', fontWeight: 600, cursor: placing?'not-allowed':'pointer' }}>{placing ? '처리중...' : '예약 매수'}</button>
                <button disabled={placing} onClick={() => placeReserve('sell')} style={{ padding: '10px 12px', borderRadius: 6, border: '1px solid #dc2626', background: '#dc2626', color: 'white', fontWeight: 600, cursor: placing?'not-allowed':'pointer' }}>{placing ? '처리중...' : '예약 매도'}</button>
              </div>
              <div style={{ marginTop: 8, fontSize: 12, color: '#6b7280' }}>
                - 예약 매수: 목표가 이하로 하락 시 체결
                <br />- 예약 매도: 목표가 이상으로 상승 시 체결
              </div>
            </div>
          )}

          {/* 예약 주문 목록 */}
          <div style={{ marginTop: 16 }}>
            <div style={{ fontWeight: 700, marginBottom: 8 }}>나의 예약 주문</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {myOrders
                .filter(o => o.isReserved && !o.isExecuted && (!currentStockId || o.stockId === currentStockId))
                .map(o => (
                  <div key={o.orderId} style={{ border: '1px solid #e5e7eb', borderRadius: 6, padding: 10, display: 'grid', gridTemplateColumns: '1fr auto', alignItems: 'center' }}>
                    <div style={{ fontSize: 13, color: '#374151' }}>
                      <div>주문번호 #{o.orderId}</div>
                      <div>유형: {o.orderType === 'BUY' ? '예약 매수' : '예약 매도'}</div>
                      <div>수량: {o.quantity}주 / 예약가: {o.price.toLocaleString()}원</div>
                    </div>
                    <button disabled={placing} onClick={() => cancelReservation(o.orderId)} style={{ padding: '8px 12px', borderRadius: 6, border: '1px solid #9ca3af', background: 'white', color: '#111827', cursor: placing?'not-allowed':'pointer', fontWeight: 600 }}>취소</button>
                  </div>
                ))}
              {myOrders.filter(o => o.isReserved && !o.isExecuted && (!currentStockId || o.stockId === currentStockId)).length === 0 && (
                <div style={{ fontSize: 12, color: '#6b7280' }}>표시할 예약 주문이 없습니다.</div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}