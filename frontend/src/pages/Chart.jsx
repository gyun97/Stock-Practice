import { useEffect, useState, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'

export default function Chart() {
  const { ticker = '' } = useParams()
  const [candleData, setCandleData] = useState([])
  const [selectedPeriod, setSelectedPeriod] = useState('MIN')
  const [loading, setLoading] = useState(false)
  const [companyName, setCompanyName] = useState('')
  const [currentPrice, setCurrentPrice] = useState(null)
  const canvasRef = useRef(null)

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

  // 기간 변경 시 데이터 다시 로드
  useEffect(() => {
    loadCompanyInfo()
    loadData(selectedPeriod)
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

      {/* 차트 */}
      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
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
    </div>
  )
}