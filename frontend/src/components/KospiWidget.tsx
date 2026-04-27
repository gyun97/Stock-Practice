import { useEffect, useRef, useState, useCallback } from 'react'
import { createChart, ISeriesApi, AreaData, Time, AreaSeries, IPriceLine } from 'lightweight-charts'

// ─── 타입 ────────────────────────────────────────────────────────────────────

type KospiInfo = {
  currentIndex: number
  change: number
  changeRate: number
  open: number
  high: number
  low: number
  prevClose: number
  high52w: number
  low52w: number
  baseDate: string
}

type KospiDataPoint = {
  date: string   // YYYYMMDD or HH:mm
  value: number
}

type Period = { key: string; label: string }

const PERIODS: Period[] = [
  { key: 'D', label: '1D' },
  { key: 'W', label: '1W' },
  { key: 'M', label: '1M' },
  { key: 'Y', label: '1Y' },
]

// ─── 유틸 ──────────────────────────────────────────────────────────────────

function toChartTime(dateStr: string): Time {
  if (!dateStr) return 0 as unknown as Time

  if (dateStr.includes(':')) {
    const [hh, mm] = dateStr.split(':').map(Number)
    return (hh * 3600 + mm * 60) as unknown as Time
  }

  if (dateStr.length >= 8) {
    return `${dateStr.slice(0, 4)}-${dateStr.slice(4, 6)}-${dateStr.slice(6, 8)}` as Time
  }

  return 0 as unknown as Time
}

function fmt(n: number, decimals = 2) {
  if (!n && n !== 0) return '—'
  return n.toLocaleString('ko-KR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
}

function formatKST() {
  return new Date().toLocaleTimeString('ko-KR', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

export default function KospiWidget() {
  const [info, setInfo] = useState<KospiInfo | null>(null)
  const [history, setHistory] = useState<KospiDataPoint[]>([])
  const [period, setPeriod] = useState('D')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [lastUpdated, setLastUpdated] = useState<string>('')
  const [isLive, setIsLive] = useState(false)

  const containerRef = useRef<HTMLDivElement | null>(null)
  const seriesRef = useRef<ISeriesApi<'Area'> | null>(null)
  const chartRef = useRef<ReturnType<typeof createChart> | null>(null)
  const priceLineRef = useRef<IPriceLine | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const apiBase = import.meta.env.VITE_API_BASE_URL || ''
  const isUp = (info?.change ?? 0) >= 0

  // ── 현재 지수 조회 ─────────────────────────────────────────────────────
  const fetchInfo = useCallback(() => {
    fetch(`${apiBase}/api/v1/stocks/kospi`)
      .then(r => r.ok ? r.json() : Promise.reject(r))
      .then(json => {
        const data = json.data ?? json
        if (data && data.currentIndex && data.currentIndex > 0) {
          setInfo(data)
          setLastUpdated(formatKST())
          setError(false)
        }
      })
      .catch(() => setError(true))
  }, [apiBase])

  // ── 기간별 히스토리 조회 ────────────────────────────────────────────────
  const fetchHistory = useCallback((p: string) => {
    setLoading(true)
    fetch(`${apiBase}/api/v1/stocks/kospi/history?period=${p}`)
      .then(r => r.ok ? r.json() : Promise.reject(r))
      .then(json => {
        const data: KospiDataPoint[] = json.data ?? json
        setHistory([...data])
        setLoading(false)
      })
      .catch(() => { setError(true); setLoading(false) })
  }, [apiBase])

  // ── 초기 로드 ──────────────────────────────────────────────────────────
  useEffect(() => {
    fetchInfo()
    fetchHistory(period)
  }, [])

  // ── 기간 변경 시 히스토리 재조회 ───────────────────────────────────────
  useEffect(() => {
    fetchHistory(period)
  }, [period])

  // ── 실시간 갱신 (1D일 때만, 30초마다) ─────────────────────────────────
  useEffect(() => {
    if (period === 'D') {
      setIsLive(true)
      intervalRef.current = setInterval(() => {
        fetchInfo()
        fetchHistory('D')
      }, 30000)
    } else {
      setIsLive(false)
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [period, fetchInfo, fetchHistory])

  // ── 차트 초기화 ─────────────────────────────────────────────────────────
  useEffect(() => {
    if (!containerRef.current) return

    const c = createChart(containerRef.current, {
      layout: {
        background: { color: 'transparent' },
        textColor: '#64748b',
      },
      grid: {
        vertLines: { color: '#f1f5f920' },
        horzLines: { color: '#f1f5f920' },
      },
      rightPriceScale: {
        borderColor: '#e2e8f030',
        scaleMargins: { top: 0.15, bottom: 0.1 },
      },
      handleScroll: false,
      handleScale: false,
      timeScale: {
        borderColor: '#e2e8f030',
        timeVisible: true,
        secondsVisible: false,
        fixLeftEdge: true,
        fixRightEdge: true,
        lockVisibleTimeRangeOnResize: true,
        tickMarkFormatter: (time: unknown) => {
          if (typeof time === 'number' && time < 86400) {
            const hh = Math.floor(time / 3600).toString().padStart(2, '0')
            const mm = Math.floor((time % 3600) / 60).toString().padStart(2, '0')
            return `${hh}:${mm}`
          }
          if (typeof time === 'string') {
            const parts = time.split('-')
            return parts.length === 3 ? `${parts[1]}/${parts[2]}` : time
          }
          return null
        },
      },
      localization: {
        timeFormatter: (time: unknown) => {
          if (typeof time === 'number' && time < 86400) {
            const hh = Math.floor(time / 3600).toString().padStart(2, '0')
            const mm = Math.floor((time % 3600) / 60).toString().padStart(2, '0')
            return `${hh}:${mm}`
          }
          if (typeof time === 'string') {
            // '2026-04-21' 형식
            const parts = time.split('-')
            if (parts.length === 3) {
              return `${parts[0]}.${parts[1]}.${parts[2]}`
            }
          }
          return String(time)
        }
      },
      crosshair: {
        vertLine: { color: '#94a3b8', width: 1, style: 2 },
        horzLine: { color: '#94a3b8', width: 1, style: 2 },
      },
      autoSize: true,
    })

    const upColor = '#ef4444'
    const downColor = '#3b82f6'
    const color = isUp ? upColor : downColor

    const series = c.addSeries(AreaSeries, {
      lineColor: color,
      topColor: `${color}40`,
      bottomColor: `${color}05`,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: true,
      crosshairMarkerVisible: true,
      crosshairMarkerRadius: 5,
    })

    seriesRef.current = series
    chartRef.current = c

    return () => {
      c.remove()
      chartRef.current = null
      seriesRef.current = null
    }
  }, [isUp])

  // ── 차트 데이터 업데이트 ────────────────────────────────────────────────
  useEffect(() => {
    if (!seriesRef.current || history.length === 0) return

    // 중복 제거 + 오름차순 정렬 (lightweight-charts 필수 조건)
    const seen = new Set<string>()
    const chartData: AreaData<Time>[] = history
      .map(d => ({ time: toChartTime(d.date), value: d.value }))
      .filter(d => {
        if (d.time === (0 as unknown as Time)) return false
        const key = String(d.time)
        if (seen.has(key)) return false
        seen.add(key)
        return true
      })
      .sort((a, b) => {
        const ta = typeof a.time === 'string' ? a.time : String(a.time)
        const tb = typeof b.time === 'string' ? b.time : String(b.time)
        return ta < tb ? -1 : ta > tb ? 1 : 0
      })

    seriesRef.current.setData(chartData)

    if (priceLineRef.current) {
      try { seriesRef.current.removePriceLine(priceLineRef.current) } catch (_) { }
      priceLineRef.current = null
    }

    if (period === 'D' && info?.prevClose) {
      priceLineRef.current = seriesRef.current.createPriceLine({
        price: info.prevClose,
        color: '#94a3b8',
        lineWidth: 1,
        lineStyle: 2,
        axisLabelVisible: true,
        title: '전일종가',
      })
    }

    chartRef.current?.timeScale().fitContent()
  }, [history, info, period])

  // ── 오류 UI ───────────────────────────────────────────────────────────
  if (error && !info) return (
    <div style={styles.card}>
      <div style={{ color: '#94a3b8', textAlign: 'center', padding: '40px 0' }}>
        <div style={{ fontSize: 32, marginBottom: 8 }}>📡</div>
        <div style={{ fontSize: 14 }}>코스피 데이터를 불러올 수 없습니다.</div>
        <button
          onClick={() => { setError(false); fetchInfo(); fetchHistory(period) }}
          style={{ marginTop: 12, padding: '6px 16px', background: '#3b82f6', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}
        >
          다시 시도
        </button>
      </div>
    </div>
  )

  // ── 메인 렌더 ────────────────────────────────────────────────────────────
  return (
    <div style={styles.card}>
      {/* 헤더 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 4 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 20, fontWeight: 800, color: '#0f172a', letterSpacing: -0.5 }}>KOSPI</span>
            {isLive && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: '#10b981', fontWeight: 600 }}>
                <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#10b981', display: 'inline-block', animation: 'pulse-dot 1.5s ease infinite' }} />
                LIVE
              </span>
            )}
          </div>
          <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>한국거래소 · KRX</div>
        </div>
        {lastUpdated && (
          <div style={{ fontSize: 11, color: '#94a3b8', textAlign: 'right', lineHeight: 1.5 }}>
            <div>마지막 업데이트</div>
            <div style={{ fontWeight: 600, color: '#64748b' }}>{lastUpdated}</div>
          </div>
        )}
      </div>

      {/* 현재 지수값 */}
      {info && info.currentIndex > 0 ? (
        <>
          <div style={{ marginTop: 12 }}>
            <div style={{ fontSize: 40, fontWeight: 900, color: '#0f172a', letterSpacing: -1, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>
              {fmt(info.currentIndex)}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
              <span style={{
                fontSize: 15, fontWeight: 700,
                color: isUp ? '#ef4444' : '#3b82f6',
                display: 'flex', alignItems: 'center', gap: 4
              }}>
                {isUp ? '▲' : '▼'}
                {isUp ? '+' : ''}{fmt(info.change)} ({isUp ? '+' : ''}{fmt(info.changeRate)}%)
              </span>
              <span style={{ fontSize: 13, color: '#94a3b8' }}>전일 대비</span>
            </div>
          </div>

          {/* 기간 탭 */}
          <div style={{ display: 'flex', gap: 4, margin: '16px 0 8px' }}>
            {PERIODS.map(p => (
              <button
                key={p.key}
                onClick={() => setPeriod(p.key)}
                style={{
                  padding: '5px 14px',
                  borderRadius: 6,
                  border: 'none',
                  background: period === p.key
                    ? (isUp ? '#fee2e2' : '#dbeafe')
                    : 'transparent',
                  color: period === p.key
                    ? (isUp ? '#ef4444' : '#3b82f6')
                    : '#64748b',
                  fontWeight: period === p.key ? 700 : 500,
                  fontSize: 13,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                {p.label}
              </button>
            ))}
          </div>
        </>
      ) : (
        <SkeletonInfo />
      )}

      {/* 차트 영역 */}
      <div style={{ position: 'relative', height: 220 }}>
        {loading && (
          <div style={{
            position: 'absolute', inset: 0, display: 'flex',
            alignItems: 'center', justifyContent: 'center',
            background: 'rgba(255,255,255,0.8)', zIndex: 2, borderRadius: 8
          }}>
            <div style={styles.spinner} />
          </div>
        )}
        {!loading && history.length === 0 && (
          <div style={{
            position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', color: '#94a3b8', fontSize: 13
          }}>
            <div style={{ fontSize: 28, marginBottom: 8 }}>📊</div>
            <div>데이터가 없습니다</div>
            <div style={{ fontSize: 11, marginTop: 4 }}>장 운영 시간: 09:00 ~ 15:30 KST</div>
          </div>
        )}
        <div ref={containerRef} style={{ width: '100%', height: 220 }} />
      </div>

      {/* 하단 통계 */}
      {info && info.currentIndex > 0 && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${info.high52w > 0 ? 3 : 2}, 1fr)`,
          gap: '10px 24px',
          marginTop: 16,
          paddingTop: 16,
          borderTop: '1px solid #f1f5f9',
        }}>
          {[
            { label: '시가', value: fmt(info.open) },
            { label: '저가', value: fmt(info.low) },
            ...(info.high52w > 0 ? [{ label: '52주 최고', value: fmt(info.high52w) }] : []),
            { label: '고가', value: fmt(info.high) },
            { label: '전일 종가', value: fmt(info.prevClose) },
            ...(info.low52w > 0 ? [{ label: '52주 최저', value: fmt(info.low52w) }] : []),
          ].map(({ label, value }) => (
            <div key={label}>
              <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 2 }}>{label}</div>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#1e293b', fontVariantNumeric: 'tabular-nums' }}>
                {value}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 장 시간 안내 (1D이면서 데이터가 없을 때) */}
      {period === 'D' && !loading && history.length === 0 && (
        <div style={{ textAlign: 'center', padding: '8px 0 0', fontSize: 12, color: '#cbd5e1' }}>
          장 운영 시간에 실시간 데이터가 표시됩니다 (09:00~15:30 KST)
        </div>
      )}
    </div>
  )
}

// ─── 서브 컴포넌트 ────────────────────────────────────────────────────────────

function SkeletonInfo() {
  return (
    <div style={{ marginTop: 12 }}>
      <div style={{ ...styles.skeleton, width: 200, height: 42, marginBottom: 10 }} />
      <div style={{ ...styles.skeleton, width: 160, height: 20 }} />
      <div style={{ display: 'flex', gap: 4, margin: '16px 0 8px' }}>
        {[1, 2, 3, 4].map(i => (
          <div key={i} style={{ ...styles.skeleton, width: 48, height: 30, borderRadius: 6 }} />
        ))}
      </div>
    </div>
  )
}

// ─── 스타일 상수 ──────────────────────────────────────────────────────────────

const styles = {
  card: {
    background: 'white',
    borderRadius: 16,
    border: '1px solid #e2e8f0',
    padding: '20px 24px',
    boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
  } as React.CSSProperties,
  skeleton: {
    background: 'linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%)',
    backgroundSize: '400% 100%',
    animation: 'skeleton-shimmer 1.4s ease infinite',
    borderRadius: 6,
  } as React.CSSProperties,
  spinner: {
    width: 28,
    height: 28,
    border: '3px solid #e2e8f0',
    borderTop: '3px solid #3b82f6',
    borderRadius: '50%',
    animation: 'spin 0.8s linear infinite',
  } as React.CSSProperties,
}
