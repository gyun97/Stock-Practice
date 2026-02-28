import { useEffect, useRef } from 'react'
import { createChart, ISeriesApi, LineData, Time, LineSeries } from 'lightweight-charts'

// 컴포넌트에 전달되는 props 타입 정의
type Props = {
  lastPrice?: number            // 마지막 체결가 (실시간으로 들어오는 값)
  lastTime?: string             // 마지막 체결 시간 (HHmmss 형태, 예: "093015")
  seed?: LineData<Time>[]       // 초기 차트에 표시할 데이터 배열
}

// LiveChart 컴포넌트
export default function LiveChart({ lastPrice, lastTime, seed }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null)   // 차트를 붙일 DOM 요소
  const seriesRef = useRef<ISeriesApi<'Line'> | null>(null)  // 라인 시리즈 객체 참조
  const chartRef = useRef<ReturnType<typeof createChart> | null>(null) // 전체 차트 객체 참조

  // 마운트 시 차트 생성
  useEffect(() => {
    if (!containerRef.current) return

    // 차트 생성 + 레이아웃 옵션 지정
    const c = createChart(containerRef.current, {
      layout: {
        background: { color: '#ffffff' },  // 배경색 (흰색)
        textColor: '#333333'               // 글자색 (진회색)
      },
      grid: {
        vertLines: { color: '#e5e7eb' },   // 세로 격자선 색상 (연회색)
        horzLines: { color: '#e5e7eb' }    // 가로 격자선 색상 (연회색)
      },
      rightPriceScale: {
        borderColor: '#d1d5db',            // 우측 가격축 경계선 색상
        scaleMargins: {                    // 가격축 위/아래 여백
          top: 0.1,
          bottom: 0.1,
        }
      },
      timeScale: {
        borderColor: '#d1d5db',            // 시간축 경계선 색상
        timeVisible: true,                 // 시간 보이게 설정
        secondsVisible: false              // 초 단위는 숨김
      },
      crosshair: {
        mode: 1,                           // 십자선 모드
        vertLine: {                        // 세로 십자선
          color: '#6b7280',
          width: 1,
          style: 2                         // 점선 스타일
        },
        horzLine: {                        // 가로 십자선
          color: '#6b7280',
          width: 1,
          style: 2
        }
      },
      autoSize: true,                      // 컨테이너 크기에 맞춰 자동 크기 조정
    })

    // 라인 시리즈 추가 (실제 주가 라인)
    const line = c.addSeries(LineSeries, {
      color: '#3b82f6',                    // 선 색상 (파란색)
      lineWidth: 2,                        // 선 굵기
      priceLineVisible: true,              // 마지막 가격 라인 표시
      lastValueVisible: true               // 마지막 값 라벨 표시
    })

    // 시리즈, 차트 객체 저장
    seriesRef.current = line
    chartRef.current = c

    // cleanup 함수 (컴포넌트 언마운트 시 차트 제거)
    return () => {
      c.remove()
      chartRef.current = null
      seriesRef.current = null
    }
  }, [])

  // 초기 데이터(seed)가 있으면 차트에 세팅
  useEffect(() => {
    if (!seriesRef.current || !seed || seed.length === 0) return
    seriesRef.current.setData(seed)  // 전체 데이터 세팅
  }, [seed])

  // 최신 가격/시간이 들어오면 차트에 업데이트
  useEffect(() => {
    if (!seriesRef.current || lastPrice == null) return
    const t = toTime(lastTime)  // HHmmss → Unix timestamp 변환
    const point: LineData<Time> = { time: t, value: Number(lastPrice) }

    console.log("LiveChart update:", lastTime, lastPrice);
    seriesRef.current.update(point)  // 새로운 데이터 포인트 반영
  }, [lastPrice, lastTime])


  // 차트를 표시할 div
  return (
    <div style={{ width: '100%', height: 500 }} ref={containerRef} />
  )
}

// "HHmmss" 형태 문자열을 lightweight-charts에서 쓰는 Time 형식으로 변환
function toTime(hhmmss?: string): Time {
  if (!hhmmss) return (Date.now() / 1000) as Time  // 없으면 현재 시간 사용
  const hh = Number(hhmmss.slice(0, 2))           // 시
  const mm = Number(hhmmss.slice(2, 4))           // 분
  const ss = Number(hhmmss.slice(4, 6))           // 초
  const now = new Date()
  now.setHours(hh, mm, ss, 0)                     // 오늘 날짜에 시간 세팅
  return (Math.floor(now.getTime() / 1000)) as Time // 초 단위 Unix timestamp 반환
}








