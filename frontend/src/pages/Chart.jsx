import { useEffect, useState, useRef, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import { createStompClient } from '../lib/socket'

export default function Chart() {
  const { ticker = '' } = useParams()
  const [candleData, setCandleData] = useState([])
  const [selectedPeriod, setSelectedPeriod] = useState('D')
  const [chartType, setChartType] = useState('candle') // 'line' | 'candle'
  const [loading, setLoading] = useState(false)
  const [companyName, setCompanyName] = useState('')
  const [logoUrl, setLogoUrl] = useState('')
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
              setLogoUrl(`/logos/${stock.companyName}.png`)
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

    // 실시간 시세 정보만 업데이트 (차트 실시간 업데이트 제거)
  }, [selectedPeriod, candleData])

  // 숫자 변환 유틸리티 함수
  function toNum(v) {
    if (v == null) return undefined
    const n = Number(String(v).replace(/[^0-9.-]/g, ''))
    return Number.isFinite(n) ? n : undefined
  }

  // 마우스 이벤트 핸들러
  const handleMouseMove = (event) => {
    const canvas = canvasRef.current
    if (!canvas || !candleData.length) return

    const rect = canvas.getBoundingClientRect()
    const x = event.clientX - rect.left
    const y = event.clientY - rect.top

    setMousePosition({ x: event.clientX, y: event.clientY })

    // 차트 영역 내에서만 처리
    const marginLeft = 60
    const marginRight = 20
    const marginTop = 20
    const marginBottom = 60
    const chartWidth = canvas.width - marginLeft - marginRight
    const chartHeight = canvas.height - marginTop - marginBottom

    if (x >= marginLeft && x <= canvas.width - marginRight && 
        y >= marginTop && y <= canvas.height - marginBottom) {
      
      // 가장 가까운 데이터 포인트 찾기
      const dataIndex = Math.round(((x - marginLeft) / chartWidth) * (candleData.length - 1))
      const clampedIndex = Math.max(0, Math.min(dataIndex, candleData.length - 1))
      
      if (candleData[clampedIndex]) {
        setHoveredData({
          index: clampedIndex,
          data: candleData[clampedIndex],
          x: x,
          y: y
        })
      }
    } else {
      setHoveredData(null)
    }
  }

  const handleMouseLeave = () => {
    setHoveredData(null)
  }

  // 데이터 로드 함수
  const loadData = async (period) => {
    if (!ticker) return

    setLoading(true)
    try {
      console.log(`데이터 로드 시작: ${ticker}, period: ${period}`)

      let response
      // 모든 기간별 데이터는 기존 API 사용
      response = await fetch(`/api/v1/stocks/${ticker}/period?period=${period}`)

      console.log('응답 상태:', response.status)

      if (!response.ok) {
        throw new Error(`데이터 로드 실패: ${response.status}`)
      }

      const result = await response.json()
      console.log('받은 데이터:', result)

      const data = result.data || []
      console.log('파싱된 캔들 데이터:', data.slice(0, 3))

      if (data.length > 0) {
        // 모든 데이터는 날짜순 정렬
        const sortedData = [...data].sort((a, b) => a.date.localeCompare(b.date))

        console.log('정렬된 데이터:', sortedData.slice(0, 3))

        // 정렬된 데이터로 상태 업데이트 및 차트 그리기
        setCandleData(sortedData)
        if (chartType === 'candle') {
          drawCandleChart(sortedData, period)
        } else {
          drawChart(sortedData, period)
        }
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

  const [hoveredData, setHoveredData] = useState(null)
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 })
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

  // 캔들 차트 그리기 함수
  const drawCandleChart = (data, period = 'D') => {
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
    const prices = data.map(d => [d.open, d.high, d.low, d.close]).flat()
    const volumes = data.map(d => d.volume)
    const maxPrice = Math.max(...prices)
    const maxVolume = Math.max(...volumes)
    const minPrice = Math.min(...prices)

    // 두 개의 분리된 차트 영역
    const priceChartHeight = chartHeight * 0.5  // 상단 50%
    const volumeChartHeight = chartHeight * 0.5  // 하단 50%
    const gapBetweenCharts = 20  // 차트 간 간격

    const priceRange = maxPrice - minPrice

    // 캔들 너비 계산
    const candleWidth = Math.max(2, chartWidth / data.length * 0.8)

    // 캔들 차트 그리기 (상단)
    data.forEach((item, index) => {
      const x = marginLeft + (index / (data.length - 1)) * chartWidth
      
      // OHLC 좌표 계산
      const openY = marginTop + priceChartHeight - ((item.open - minPrice) / priceRange) * priceChartHeight
      const highY = marginTop + priceChartHeight - ((item.high - minPrice) / priceRange) * priceChartHeight
      const lowY = marginTop + priceChartHeight - ((item.low - minPrice) / priceRange) * priceChartHeight
      const closeY = marginTop + priceChartHeight - ((item.close - minPrice) / priceRange) * priceChartHeight

      // 상승/하락 색상 결정
      const isUp = item.close >= item.open
      const candleColor = isUp ? '#e74c3c' : '#3498db'  // 빨간색(상승) / 파란색(하락)
      const wickColor = isUp ? '#c0392b' : '#2980b9'    // 심지 색상

      // 심지 그리기 (위아래 선)
      ctx.strokeStyle = wickColor
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(x, highY)
      ctx.lineTo(x, lowY)
      ctx.stroke()

      // 캔들 몸통 그리기
      const bodyTop = Math.min(openY, closeY)
      const bodyHeight = Math.abs(closeY - openY)
      
      if (bodyHeight < 1) {
        // 도지(Doji) - 몸통이 없는 경우
        ctx.strokeStyle = candleColor
        ctx.lineWidth = 1
        ctx.beginPath()
        ctx.moveTo(x - candleWidth/2, openY)
        ctx.lineTo(x + candleWidth/2, openY)
        ctx.stroke()
      } else {
        // 일반 캔들
        ctx.fillStyle = candleColor
        ctx.fillRect(x - candleWidth/2, bodyTop, candleWidth, bodyHeight)
        
        // 캔들 테두리
        ctx.strokeStyle = candleColor
        ctx.lineWidth = 1
        ctx.strokeRect(x - candleWidth/2, bodyTop, candleWidth, bodyHeight)
      }
    })

    // 주가 Y축 라벨
    ctx.fillStyle = '#666'
    ctx.font = '12px Arial'
    ctx.textAlign = 'right'

    for (let i = 0; i <= 4; i++) {
      const value = maxPrice - (i / 4) * (maxPrice - minPrice)
      const y = marginTop + priceChartHeight - (i / 4) * priceChartHeight + 4
      ctx.fillText(Math.round(value).toLocaleString(), marginLeft - 10, y)
    }

    // 주가 제목
    ctx.fillStyle = '#333'
    ctx.font = 'bold 12px Arial'
    ctx.textAlign = 'left'
    ctx.fillText('주가 (캔들)', marginLeft, marginTop - 5)

    // 거래량 막대 그래프 그리기 (하단) - 전일 대비 변화 반영
    const volumeStartY = marginTop + priceChartHeight + gapBetweenCharts
    const barWidth = chartWidth / data.length * 0.8

    data.forEach((item, index) => {
      const x = marginLeft + (index / (data.length - 1)) * chartWidth - barWidth / 2
      const barHeight = (item.volume / maxVolume) * volumeChartHeight
      const y = volumeStartY + volumeChartHeight - barHeight

      // 전일 대비 거래량 변화에 따른 색상 결정
      let barColor = '#ff6b6b' // 기본 색상 (빨간색)
      
      if (index > 0) {
        const prevVolume = data[index - 1].volume
        const currentVolume = item.volume
        
        if (currentVolume > prevVolume) {
          // 거래량 증가 - 빨간색 (상승)
          barColor = '#e74c3c'
        } else if (currentVolume < prevVolume) {
          // 거래량 감소 - 파란색 (하락)
          barColor = '#3498db'
        } else {
          // 거래량 동일 - 회색
          barColor = '#95a5a6'
        }
      }

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
      // 기간별 날짜 표시 형식
      const dateStr = data[index].date
      const year = dateStr.substring(0, 4)
      const month = dateStr.substring(4, 6)
      const day = dateStr.substring(6, 8)
      
      if (period === 'M' || period === 'Y') {
        // 월 단위, 년 단위는 연도 포함
        labelText = `${year}-${month}`
      } else {
        // 일 단위, 주 단위는 월-일만
        labelText = `${month}-${day}`
      }

      ctx.fillText(labelText, x, y)
    }
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

      // 전일 대비 거래량 변화에 따른 색상 결정
      let barColor = '#ff6b6b' // 기본 색상 (빨간색)
      
      if (index > 0) {
        const prevVolume = data[index - 1].volume
        const currentVolume = item.volume
        
        if (currentVolume > prevVolume) {
          // 거래량 증가 - 빨간색 (상승)
          barColor = '#e74c3c'
        } else if (currentVolume < prevVolume) {
          // 거래량 감소 - 파란색 (하락)
          barColor = '#3498db'
        } else {
          // 거래량 동일 - 회색
          barColor = '#95a5a6'
        }
      }

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
      // 기간별 날짜 표시 형식
      const dateStr = data[index].date
      const year = dateStr.substring(0, 4)
      const month = dateStr.substring(4, 6)
      const day = dateStr.substring(6, 8)
      
      if (period === 'M' || period === 'Y') {
        // 월 단위, 년 단위는 연도 포함
        labelText = `${year}-${month}`
      } else {
        // 일 단위, 주 단위는 월-일만
        labelText = `${month}-${day}`
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

  // 차트 타입 변경 시 차트 다시 그리기
  useEffect(() => {
    if (candleData.length > 0) {
      if (chartType === 'candle') {
        drawCandleChart(candleData, selectedPeriod)
      } else {
        drawChart(candleData, selectedPeriod)
      }
    }
  }, [chartType])

  return (
    <div style={{ maxWidth: 1400, margin: '0 auto', padding: 16 }}>
      {/* 헤더 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Link to="/" style={{ textDecoration: 'none', color: '#666' }}>← 목록</Link>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {logoUrl && (
              <img 
                src={logoUrl} 
                alt={companyName} 
                style={{ 
                  width: 32, 
                  height: 32, 
                  objectFit: 'contain',
                  borderRadius: 4
                }} 
                onError={(e) => {
                  e.target.style.display = 'none'
                }}
              />
            )}
            <h1 style={{ margin: 0, fontSize: 24, display: 'flex', alignItems: 'center', gap: 8 }}>
              {companyName || ticker}
              {ticker && (
                <span style={{ 
                  fontSize: 14, 
                  color: '#9ca3af', 
                  fontWeight: 'normal',
                  marginLeft: 4
                }}>
                  {ticker}
                </span>
              )}
            </h1>
          </div>
        </div>
      </div>

      {/* 실시간 시세 정보와 버튼들을 같은 라인에 배치 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
        {/* 실시간 시세 정보 */}
        {currentPrice && (
          <div style={{
            background: 'white',
            borderRadius: 8,
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            maxWidth: 1200
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
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
                    ({currentPrice.changeRate >= 0 ? '+' : ''}{currentPrice.changeRate.toFixed(2)}%)
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
            <div style={{ textAlign: 'right', marginLeft: 24 }}>
              <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>체결시간</div>
              <div style={{ fontSize: 14, color: '#333' }}>
                {currentPrice.tradeTime || '실시간'}
              </div>
            </div>
          </div>
        )}

        {/* 기간 선택 버튼과 차트 타입 선택 버튼을 함께 배치 */}
        <div style={{ display: 'flex', gap: 8 }}>
          {/* 기간 선택 버튼 */}
          {periods.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setSelectedPeriod(key)}
              style={{
                padding: '8px 16px',
                border: 'none',
                borderRadius: 4,
                background: selectedPeriod === key ? '#e5e7eb' : 'white',
                color: selectedPeriod === key ? '#374151' : '#333',
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: selectedPeriod === key ? 'bold' : 'normal',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)'
              }}
            >
              {label}
            </button>
          ))}
          
          {/* 차트 타입 토글 */}
          <div style={{ 
            display: 'flex', 
            background: '#f3f4f6', 
            borderRadius: 6, 
            padding: 2,
            boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)'
          }}>
            <button
              onClick={() => setChartType('candle')}
              style={{
                padding: '8px 12px',
                border: 'none',
                borderRadius: 4,
                background: chartType === 'candle' ? '#e5e7eb' : 'transparent',
                color: chartType === 'candle' ? '#374151' : '#6b7280',
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: chartType === 'candle' ? 'bold' : 'normal',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                transition: 'all 0.2s ease'
              }}
            >
              <span style={{ fontSize: 16 }}>🕯️</span>
              캔들
            </button>
            <button
              onClick={() => setChartType('line')}
              style={{
                padding: '8px 12px',
                border: 'none',
                borderRadius: 4,
                background: chartType === 'line' ? '#e5e7eb' : 'transparent',
                color: chartType === 'line' ? '#374151' : '#6b7280',
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: chartType === 'line' ? 'bold' : 'normal',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                transition: 'all 0.2s ease'
              }}
            >
              <span style={{ fontSize: 16 }}>📈</span>
              라인
            </button>
          </div>
        </div>
      </div>

      {/* 로딩 상태 */}
      {loading && (
        <div style={{ textAlign: 'center', padding: 40, color: '#666' }}>
          데이터를 불러오는 중...
        </div>
      )}

      {/* 차트 + 우측 주문패널 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 16, alignItems: 'start' }}>
        <div>
          <canvas
            ref={canvasRef}
            width={1200}
            height={800}
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
            style={{
              border: '1px solid #ddd',
              borderRadius: 8,
              marginBottom: 20,
              background: 'white',
              width: '100%',
              maxWidth: 1200,
              cursor: 'crosshair'
            }}
          />
          
          {/* 실시간 데이터 테이블 */}
          <div style={{ marginTop: 20, border: '1px solid #e5e7eb', borderRadius: 8, background: '#ffffff', overflow: 'hidden' }}>
            <div style={{ maxHeight: 400, overflowY: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                <thead>
                  <tr style={{ background: '#f9fafb' }}>
                    <th style={{ padding: '8px 12px', textAlign: 'left', borderBottom: '1px solid #e5e7eb', fontWeight: 600 }}>날짜/시간</th>
                    <th style={{ padding: '8px 12px', textAlign: 'right', borderBottom: '1px solid #e5e7eb', fontWeight: 600 }}>시가</th>
                    <th style={{ padding: '8px 12px', textAlign: 'right', borderBottom: '1px solid #e5e7eb', fontWeight: 600 }}>고가</th>
                    <th style={{ padding: '8px 12px', textAlign: 'right', borderBottom: '1px solid #e5e7eb', fontWeight: 600 }}>저가</th>
                    <th style={{ padding: '8px 12px', textAlign: 'right', borderBottom: '1px solid #e5e7eb', fontWeight: 600 }}>종가</th>
                    <th style={{ padding: '8px 12px', textAlign: 'right', borderBottom: '1px solid #e5e7eb', fontWeight: 600 }}>거래량</th>
                  </tr>
                </thead>
                <tbody>
                  {candleData.slice().reverse().map((data, index) => (
                    <tr key={index} style={{ borderBottom: '1px solid #f3f4f6' }}>
                      <td style={{ padding: '8px 12px', color: '#6b7280', fontSize: 11 }}>
                        {selectedPeriod === 'D' || selectedPeriod === 'W' || selectedPeriod === 'M' || selectedPeriod === 'Y' ? 
                          data.date ? 
                            `${data.date.substring(0, 4)}-${data.date.substring(4, 6)}-${data.date.substring(6, 8)}` :
                            `데이터 ${index + 1}` :
                          data.timeStr ? 
                            data.timeStr.length === 6 ? 
                              `${data.timeStr.substring(0, 2)}:${data.timeStr.substring(2, 4)}` :
                              data.timeStr.length === 4 ? 
                                `${data.timeStr.substring(0, 2)}:${data.timeStr.substring(2, 4)}` :
                                data.timeStr :
                            `시간 ${index + 1}`
                        }
                      </td>
                      <td style={{ padding: '8px 12px', textAlign: 'right', fontWeight: 500 }}>
                        {data.open?.toLocaleString() || '-'}
                      </td>
                      <td style={{ padding: '8px 12px', textAlign: 'right', fontWeight: 500, color: '#dc2626' }}>
                        {data.high?.toLocaleString() || '-'}
                      </td>
                      <td style={{ padding: '8px 12px', textAlign: 'right', fontWeight: 500, color: '#2563eb' }}>
                        {data.low?.toLocaleString() || '-'}
                      </td>
                      <td style={{ padding: '8px 12px', textAlign: 'right', fontWeight: 500 }}>
                        {data.close?.toLocaleString() || '-'}
                      </td>
                      <td style={{ padding: '8px 12px', textAlign: 'right', color: '#6b7280' }}>
                        {data.volume ? Math.round(data.volume / 1000000 * 100) / 100 + 'M' : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
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

        </div>
      </div>

      {/* 툴팁 */}
      {hoveredData && (
        <div
          style={{
            position: 'fixed',
            left: mousePosition.x + 10,
            top: mousePosition.y - 10,
            background: 'rgba(0, 0, 0, 0.8)',
            color: 'white',
            padding: '8px 12px',
            borderRadius: 6,
            fontSize: 12,
            zIndex: 1000,
            pointerEvents: 'none',
            minWidth: 200
          }}
        >
          <div style={{ fontWeight: 'bold', marginBottom: 4 }}>
            {selectedPeriod === 'D' || selectedPeriod === 'W' || selectedPeriod === 'M' || selectedPeriod === 'Y' ? 
              hoveredData.data.date ? 
                `${hoveredData.data.date.substring(0, 4)}-${hoveredData.data.date.substring(4, 6)}-${hoveredData.data.date.substring(6, 8)}` :
                `데이터 ${hoveredData.index + 1}` :
              `데이터 ${hoveredData.index + 1}`
            }
          </div>
          
          {chartType === 'candle' ? (
            <div style={{ fontSize: 11 }}>
              <div>시가: {hoveredData.data.open?.toLocaleString()}원</div>
              <div>고가: {hoveredData.data.high?.toLocaleString()}원</div>
              <div>저가: {hoveredData.data.low?.toLocaleString()}원</div>
              <div>종가: {hoveredData.data.close?.toLocaleString()}원</div>
              <div>거래량: {Math.round((hoveredData.data.volume || 0) / 1000000)}M</div>
            </div>
          ) : (
            <div style={{ fontSize: 11 }}>
              <div>종가: {hoveredData.data.close?.toLocaleString()}원</div>
              <div>거래량: {Math.round((hoveredData.data.volume || 0) / 1000000)}M</div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}