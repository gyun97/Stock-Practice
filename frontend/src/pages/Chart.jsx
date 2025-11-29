import { useEffect, useState, useRef, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import { createStompClient } from '../lib/socket'
import { createChart, CandlestickSeries, HistogramSeries } from 'lightweight-charts'
import { tokenManager } from '../lib/tokenManager'

export default function Chart() {
  const { ticker = '' } = useParams()
  const [candleData, setCandleData] = useState([])
  const [selectedPeriod, setSelectedPeriod] = useState('D')
  const [loading, setLoading] = useState(false)
  const [companyName, setCompanyName] = useState('')
  const [logoUrl, setLogoUrl] = useState('')
  const [currentStockId, setCurrentStockId] = useState(null)
  const [currentPrice, setCurrentPrice] = useState(null)
  const [outline, setOutline] = useState('')
  const [orderTab, setOrderTab] = useState('market') // 'market' | 'reserve'
  const [qty, setQty] = useState(1)
  const [reserveQty, setReserveQty] = useState(1)
  const [reservePrice, setReservePrice] = useState('')
  const [placing, setPlacing] = useState(false)
  const [orderMsg, setOrderMsg] = useState('')
  const [orderErr, setOrderErr] = useState('')
  const [tooltip, setTooltip] = useState({ visible: false, x: 0, y: 0, data: null })
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [hasMoreData, setHasMoreData] = useState(true)
  const chartContainerRef = useRef(null)
  const chartRef = useRef(null)
  const candlestickSeriesRef = useRef(null)
  const volumeSeriesRef = useRef(null)
  const stompRef = useRef(null)
  const tickerRef = useRef(ticker)
  const candleDataRef = useRef(candleData)
  const selectedPeriodRef = useRef(selectedPeriod)
  const hasMoreDataRef = useRef(hasMoreData)
  const isLoadingMoreRef = useRef(isLoadingMore)

  // ticker 변경 시 ref 업데이트
  useEffect(() => {
    tickerRef.current = ticker
  }, [ticker])

  // candleData 변경 시 ref 업데이트
  useEffect(() => {
    candleDataRef.current = candleData
  }, [candleData])

  // selectedPeriod 변경 시 ref 업데이트
  useEffect(() => {
    selectedPeriodRef.current = selectedPeriod
  }, [selectedPeriod])

  // hasMoreData 변경 시 ref 업데이트
  useEffect(() => {
    hasMoreDataRef.current = hasMoreData
  }, [hasMoreData])

  // isLoadingMore 변경 시 ref 업데이트
  useEffect(() => {
    isLoadingMoreRef.current = isLoadingMore
  }, [isLoadingMore])

  // 로그인 상태 확인
  const checkLoginStatus = () => {
    const token = tokenManager.getAccessToken()
    setIsLoggedIn(!!token)
  }



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
      
      // 기업 개요 로드
      try {
        const outlineResponse = await fetch(`/api/v1/stocks/${ticker}/outline`)
        if (outlineResponse.ok) {
          const outlineResult = await outlineResponse.json()
          console.log('기업 개요 응답:', outlineResult)
          console.log('기업 개요 data:', outlineResult.data)
          console.log('기업 개요 data 타입:', typeof outlineResult.data)
          // null이거나 undefined가 아니고, 빈 문자열이 아닐 때만 설정
          if (outlineResult.data !== null && outlineResult.data !== undefined && outlineResult.data.trim() !== '') {
            setOutline(outlineResult.data)
          } else {
            console.log('기업 개요 데이터가 없습니다. (null 또는 빈 문자열)')
            setOutline('')
          }
        } else {
          console.log('기업 개요 API 호출 실패:', outlineResponse.status)
          setOutline('')
        }
      } catch (error) {
        console.error('기업 개요 로드 실패:', error)
        setOutline('')
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

  // 날짜 형식 변환 함수 (20250717 → 2025-07-17)
  function formatDate(dateStr) {
    if (!dateStr || dateStr.length !== 8) return dateStr
    const year = dateStr.substring(0, 4)
    const month = dateStr.substring(4, 6)
    const day = dateStr.substring(6, 8)
    return `${year}-${month}-${day}`
  }

  // lightweight-charts 초기화
  const initializeChart = () => {
    if (!chartContainerRef.current || chartRef.current) return

    console.log('차트 초기화 시작')

    const chart = createChart(chartContainerRef.current, {
      width: chartContainerRef.current.clientWidth,
      height: 800, // 높이 증가
      layout: {
        backgroundColor: '#ffffff',
        textColor: '#333',
      },
      grid: {
        vertLines: {
          color: '#f0f0f0',
        },
        horzLines: {
          color: '#f0f0f0',
        },
      },
      crosshair: {
        mode: 1,
      },
      rightPriceScale: {
        borderColor: '#cccccc',
      },
      timeScale: {
        borderColor: '#cccccc',
        timeVisible: true,
        secondsVisible: false,
        barSpacing: 8, // 캔들 간격 증가
        minBarSpacing: 2, // 최소 캔들 간격
      },
    })

    // 캔들스틱 시리즈 추가
    const candlestickSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#e74c3c',
      downColor: '#3498db',
      borderDownColor: '#2980b9',
      borderUpColor: '#c0392b',
      wickDownColor: '#2980b9',
      wickUpColor: '#c0392b',
      priceFormat: {
        type: 'price',
        precision: 0,
        minMove: 1,
      },
    })

    // 볼륨 시리즈 추가 (별도 패널)
    const volumeSeries = chart.addSeries(HistogramSeries, {
      color: '#26a69a',
      priceFormat: {
        type: 'volume',
      },
      priceScaleId: 'volume', // 별도 가격 스케일 사용
      scaleMargins: {
        top: 0.1,
        bottom: 0,
      },
    })

    // 볼륨용 별도 패널 생성
    chart.priceScale('volume').applyOptions({
      scaleMargins: {
        top: 0.8,
        bottom: 0,
      },
    })

      chartRef.current = chart
      candlestickSeriesRef.current = candlestickSeries
      volumeSeriesRef.current = volumeSeries

      // 마우스 이벤트 리스너 추가
      chart.subscribeCrosshairMove((param) => {
        if (param.point === undefined || !param.time || param.point.x < 0 || param.point.y < 0) {
          setTooltip({ visible: false, x: 0, y: 0, data: null })
          return
        }

        const data = param.seriesData.get(candlestickSeries)
        if (data) {
          setTooltip({
            visible: true,
            x: param.point.x,
            y: param.point.y,
            data: {
              time: data.time,
              open: data.open,
              high: data.high,
              low: data.low,
              close: data.close,
              volume: param.seriesData.get(volumeSeries)?.value || 0
            }
          })
        }
      })

      // 스크롤 이벤트 리스너 추가 (무한 스크롤)
      const cleanupScroll = chart.timeScale().subscribeVisibleLogicalRangeChange(async (logicalRange) => {
        console.log('스크롤 이벤트 발생:', logicalRange)
        
        // 최신 상태 참조
        const currentCandleData = candleDataRef.current
        const currentHasMoreData = hasMoreDataRef.current
        const currentIsLoadingMore = isLoadingMoreRef.current
        const currentTicker = tickerRef.current
        const currentPeriod = selectedPeriodRef.current
        
        if (currentCandleData.length === 0) {
          console.log('candleData가 없어서 스크롤 처리 건너뜀')
          return
        }
        
        if (logicalRange && logicalRange.from !== null) {
          // 차트의 왼쪽 끝에 도달했을 때 추가 데이터 로드
          const threshold = 10 // 왼쪽 끝에서 10개 데이터 지점 전에 로드 시작
          
          console.log('스크롤 조건 확인:', {
            from: logicalRange.from,
            threshold,
            hasMoreData: currentHasMoreData,
            isLoadingMore: currentIsLoadingMore,
            조건만족: logicalRange.from <= threshold && currentHasMoreData && !currentIsLoadingMore
          })
          
          if (logicalRange.from <= threshold && currentHasMoreData && !currentIsLoadingMore) {
            console.log('차트 왼쪽 끝 도달, 추가 데이터 로드 시작')
            
            // 직접 데이터 로드 로직 실행
            setIsLoadingMore(true)
            try {
              const sortedData = [...currentCandleData].sort((a, b) => a.date.localeCompare(b.date))
              const oldestDate = sortedData[0].date
              
              console.log(`추가 데이터 로드 시작: ${currentTicker}, endDate: ${oldestDate}`)
              
              const year = oldestDate.substring(0, 4)
              const month = oldestDate.substring(4, 6)
              const day = oldestDate.substring(6, 8)
              const dateObj = new Date(`${year}-${month}-${day}`)
              dateObj.setDate(dateObj.getDate() - 100)
              const startDateStr = dateObj.toISOString().split('T')[0].replace(/-/g, '')
              const endDateStr = (parseInt(oldestDate) - 1).toString()

              const response = await fetch(`/api/v1/stocks/${currentTicker}/period/range?period=${currentPeriod}&startDate=${startDateStr}&endDate=${endDateStr}`)
              
              if (!response.ok) {
                throw new Error(`추가 데이터 로드 실패: ${response.status}`)
              }

              const result = await response.json()
              const newData = result.data || []
              console.log('API 응답 데이터:', newData.length, '개')
              
              if (newData.length === 0) {
                console.log('더 이상 데이터가 없음')
                setHasMoreData(false)
                return
              }

              const sortedNewData = [...newData].sort((a, b) => a.date.localeCompare(b.date))
              const combinedData = [...sortedNewData, ...currentCandleData]
              const uniqueData = combinedData.filter((item, index, self) => 
                index === self.findIndex(t => t.date === item.date)
              ).sort((a, b) => a.date.localeCompare(b.date))

              console.log('데이터 병합 완료:', { 
                기존데이터: currentCandleData.length, 
                새데이터: newData.length, 
                병합후: uniqueData.length 
              })

              setCandleData(uniqueData)

              // 차트에 새 데이터 추가
              if (candlestickSeriesRef.current && volumeSeriesRef.current) {
                const chartData = uniqueData.map(item => ({
                  time: formatDate(item.date),
                  open: item.open,
                  high: item.high,
                  low: item.low,
                  close: item.close,
                }))

                const volumeData = uniqueData.map((item, index) => {
                  let color = '#26a69a'
                  
                  if (index > 0) {
                    const prevVolume = uniqueData[index - 1].volume
                    const currentVolume = item.volume
                    
                    if (currentVolume > prevVolume) {
                      color = '#e74c3c'
                    } else if (currentVolume < prevVolume) {
                      color = '#3498db'
                    }
                  }
                  
                  return {
                    time: formatDate(item.date),
                    value: item.volume,
                    color: color
                  }
                })

                console.log('차트에 새 데이터 설정:', chartData.length, '개')
                
                const currentRange = chartRef.current?.timeScale()?.getVisibleLogicalRange()
                
                candlestickSeriesRef.current.setData(chartData)
                volumeSeriesRef.current.setData(volumeData)
                
                // 화면 범위 복원
                if (currentRange && chartRef.current) {
                  setTimeout(() => {
                    const newFrom = currentRange.from + newData.length
                    const newTo = currentRange.to + newData.length
                    chartRef.current.timeScale().setVisibleLogicalRange({
                      from: newFrom,
                      to: newTo
                    })
                  }, 50)
                }
              }

              console.log(`추가 데이터 로드 완료: ${newData.length}개 추가`)
            } catch (error) {
              console.error('추가 데이터 로드 실패:', error)
            } finally {
              setIsLoadingMore(false)
            }
          }
        }
      })

      console.log('차트 초기화 완료')

      // 차트 크기 조정
      const handleResize = () => {
        if (chartContainerRef.current && chartRef.current) {
          chartRef.current.applyOptions({
            width: chartContainerRef.current.clientWidth,
          })
        }
      }

      window.addEventListener('resize', handleResize)

      return () => {
        console.log('차트 정리 시작')
        window.removeEventListener('resize', handleResize)
        if (cleanupScroll) cleanupScroll()
        if (chartRef.current) {
          chartRef.current.remove()
          chartRef.current = null
          candlestickSeriesRef.current = null
          volumeSeriesRef.current = null
        }
        console.log('차트 정리 완료')
      }
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

      if (data.length === 0) {
        console.log('데이터가 없음')
        setCandleData([])
        setHasMoreData(false)
        return
      }

      if (data.length > 0) {
        // 모든 데이터는 날짜순 정렬
        const sortedData = [...data].sort((a, b) => a.date.localeCompare(b.date))

        console.log('정렬된 데이터:', sortedData.slice(0, 3))

        // 정렬된 데이터로 상태 업데이트
        setCandleData(sortedData)
        
        // 항상 더 많은 데이터를 로드할 수 있다고 가정 (초기 로드 시에만)
        // 실제로 데이터가 없으면 스크롤 리스너에서 hasMoreData를 false로 설정
        setHasMoreData(true)

        // lightweight-charts에 데이터 설정
        if (candlestickSeriesRef.current && volumeSeriesRef.current) {
          console.log('차트에 데이터 설정 시작:', sortedData.length, '개')
          
          const chartData = sortedData.map(item => ({
            time: formatDate(item.date), // 날짜 형식 변환
            open: item.open,
            high: item.high,
            low: item.low,
            close: item.close,
          }))

          const volumeData = sortedData.map((item, index) => {
            // 전날 거래량과 비교하여 색상 결정
            let color = '#26a69a' // 기본 색상 (회색)
            
            if (index > 0) {
              const prevVolume = sortedData[index - 1].volume
              const currentVolume = item.volume
              
              if (currentVolume > prevVolume) {
                color = '#e74c3c' // 전날 대비 거래량 증가 (빨강)
              } else if (currentVolume < prevVolume) {
                color = '#3498db' // 전날 대비 거래량 감소 (파랑)
              }
            }
            
            return {
              time: formatDate(item.date),
              value: item.volume,
              color: color
            }
          })

          console.log('캔들 데이터:', chartData.slice(0, 3))
          console.log('볼륨 데이터:', volumeData.slice(0, 3))

          candlestickSeriesRef.current.setData(chartData)
          volumeSeriesRef.current.setData(volumeData)
          
          // 차트 기본 줌 설정 (최근 데이터에 포커스)
          setTimeout(() => {
            if (chartRef.current && chartData.length > 0) {
              // 최근 30개 데이터만 보이도록 설정
              const visibleRange = Math.min(30, chartData.length)
              chartRef.current.timeScale().setVisibleLogicalRange({
                from: chartData.length - visibleRange,
                to: chartData.length - 1
              })
            }
          }, 100)
          
          console.log('차트에 데이터 설정 완료')
        } else {
          console.log('차트 시리즈가 아직 준비되지 않음')
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

  const getToken = () => tokenManager.getAccessToken()
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
      const res = await tokenManager.authenticatedFetch(url, { method: 'POST' })
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
      const res = await tokenManager.authenticatedFetch(url, { method: 'POST' })
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

  // 로그인 상태 확인
  useEffect(() => {
    checkLoginStatus()
  }, [])

  // 차트 초기화
  useEffect(() => {
    console.log('차트 초기화 useEffect 실행')
    const cleanup = initializeChart()
    return cleanup
  }, [])

    // 차트가 초기화된 후 데이터 로드
  useEffect(() => {
    console.log('데이터 로드 useEffect 실행:', {
      chartRef: !!chartRef.current,
      candlestickSeriesRef: !!candlestickSeriesRef.current,
      volumeSeriesRef: !!volumeSeriesRef.current,
      selectedPeriod,
      ticker
    })
    
    if (chartRef.current && candlestickSeriesRef.current && volumeSeriesRef.current && ticker) {
      console.log('차트 준비 완료, 데이터 로드 시작')
      // 차트 초기화 완료 후 약간의 지연을 두고 데이터 로드
      setTimeout(() => {
        loadData(selectedPeriod)
      }, 100)
    } else {
      console.log('차트 준비 미완료로 데이터 로드 건너뜀')
    }
  }, [selectedPeriod, ticker])

  // 기간 변경 시 데이터 다시 로드
  // 기간 변경 시 무한 스크롤 상태 초기화
  useEffect(() => {
    console.log('기간 변경됨:', selectedPeriod)
    setHasMoreData(true)
    setIsLoadingMore(false)
  }, [selectedPeriod])

  useEffect(() => {
    console.log('Chart 페이지 마운트, ticker:', ticker)
    loadCompanyInfo()
  }, [ticker])

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
                <div style={{ fontSize: 12, color: '#666', marginBottom: 4, marginLeft: '16px' }}>현재가</div>
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
                <div style={{ fontSize: 12, color: '#666', marginBottom: 4, marginLeft: '16px' }}>등락률</div>
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
        </div>
      </div>

      {/* 로딩 상태 */}
      {loading && (
        <div style={{ textAlign: 'center', padding: 40, color: '#666' }}>
          데이터를 불러오는 중...
        </div>
      )}

      {/* 추가 데이터 로딩 상태 */}
      {isLoadingMore && (
        <div style={{ textAlign: 'center', padding: 20, color: '#666', fontSize: 14 }}>
          과거 데이터를 불러오는 중...
        </div>
      )}

      {/* 차트 + 우측 주문패널 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 16, alignItems: 'start' }}>
        <div>
          <div
            ref={chartContainerRef}
            style={{
              width: '100%',
              height: '800px',
              border: '1px solid #ddd',
              borderRadius: 8,
              marginBottom: 20,
              background: 'white',
              minHeight: '800px',
              position: 'relative'
            }}
          >
            {/* 툴팁 */}
            {tooltip.visible && tooltip.data && (
              <div
                style={{
                  position: 'absolute',
                  left: tooltip.x + 10,
                  top: tooltip.y - 10,
                  background: 'rgba(0, 0, 0, 0.8)',
                  color: 'white',
                  padding: '8px 12px',
                  borderRadius: 6,
                  fontSize: 12,
                  pointerEvents: 'none',
                  zIndex: 1000,
                  minWidth: 200,
                  boxShadow: '0 2px 8px rgba(0, 0, 0, 0.3)'
                }}
              >
                <div style={{ marginBottom: 4, fontWeight: 'bold' }}>
                  {tooltip.data.time}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <div>
                    <div style={{ color: '#ccc', fontSize: 10 }}>시가</div>
                    <div>{tooltip.data.open?.toLocaleString()}</div>
                  </div>
                  <div>
                    <div style={{ color: '#ccc', fontSize: 10 }}>고가</div>
                    <div style={{ color: '#e74c3c' }}>{tooltip.data.high?.toLocaleString()}</div>
                  </div>
                  <div>
                    <div style={{ color: '#ccc', fontSize: 10 }}>저가</div>
                    <div style={{ color: '#3498db' }}>{tooltip.data.low?.toLocaleString()}</div>
                  </div>
                  <div>
                    <div style={{ color: '#ccc', fontSize: 10 }}>종가</div>
                    <div>{tooltip.data.close?.toLocaleString()}</div>
                  </div>
                </div>
                <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid #555' }}>
                  <div style={{ color: '#ccc', fontSize: 10 }}>거래량</div>
                  <div>{tooltip.data.volume ? Math.round(tooltip.data.volume / 1000000 * 100) / 100 + 'M' : '0'}</div>
                </div>
              </div>
            )}
          </div>
          
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

        {/* 우측 패널: 기업 개요 + 매수/매도 */}
        <div style={{ position: 'sticky', top: 16, display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* 기업 개요 UI */}
          <div style={{ 
            border: '1px solid #e5e7eb', 
            borderRadius: 8, 
            background: '#ffffff',
            padding: 16
          }}>
            <div style={{ 
              fontSize: 16, 
              fontWeight: 600, 
              color: '#374151', 
              marginBottom: 12 
            }}>
              기업 개요
            </div>
            {outline && outline.trim() !== '' ? (
              <div style={{ 
                fontSize: 14, 
                color: '#111827', 
                lineHeight: 1.7
              }}>
                {outline}
              </div>
            ) : (
              <div style={{ 
                fontSize: 14, 
                color: '#9ca3af', 
                textAlign: 'center',
                fontStyle: 'italic',
                padding: '20px 0'
              }}>
                기업 개요 정보가 없습니다.
              </div>
            )}
          </div>
          
          {/* 매수/매도 창 */}
          <div style={{ border: '1px solid #e5e7eb', borderRadius: 8, background: '#ffffff', padding: 16 }}>
          {!isLoggedIn ? (
            <div style={{ textAlign: 'center', padding: '40px 20px' }}>
              <div style={{ fontSize: 16, color: '#666', marginBottom: 16 }}>
                🔒 로그인한 사용자만 주문이 가능합니다
              </div>
              <Link 
                to="/login" 
                style={{ 
                  display: 'inline-block',
                  padding: '12px 24px', 
                  background: '#2962FF', 
                  color: 'white', 
                  textDecoration: 'none', 
                  borderRadius: 6,
                  fontWeight: 600,
                  fontSize: 14
                }}
              >
                로그인하기
              </Link>
            </div>
          ) : (
            <>
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
            </>
          )}
          </div>
        </div>
      </div>
    </div>
  )
}