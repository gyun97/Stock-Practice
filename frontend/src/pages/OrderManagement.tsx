import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { tokenManager } from '../lib/tokenManager'

type Order = {
  orderId: number
  userId: number
  stockId: number
  stockName: string
  price: number
  quantity: number
  totalPrice: number
  orderType: 'BUY' | 'SELL'
  executed: boolean
  reserved: boolean
  createdAt: string
  updatedAt: string
}

type Stock = {
  stockId: number
  ticker: string
  name: string
}

export default function OrderManagement() {
  const [orders, setOrders] = useState<Order[]>([])
  const [stocks, setStocks] = useState<Stock[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cancelLoading, setCancelLoading] = useState<number | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    fetchOrders()
    fetchStocks()
  }, [])

  const fetchOrders = async () => {
    try {
      const response = await tokenManager.authenticatedFetch('/api/v1/orders')

      if (response.ok) {
        const result = await response.json()
        const ordersData = result.data || []

        // 디버깅: 주문 데이터 로그 출력
        console.log('전체 주문 데이터:', ordersData)
        console.log('예약 주문:', ordersData.filter((order: Order) => order.reserved))
        console.log('일반 주문:', ordersData.filter((order: Order) => !order.reserved))

        setOrders(ordersData)
      } else if (response.status === 401) {
        setError('로그인이 만료되었습니다. 다시 로그인해주세요.')
      } else {
        setError(`주문 내역을 불러오는데 실패했습니다. (상태 코드: ${response.status})`)
      }
    } catch (err) {
      console.error('주문 내역 조회 오류:', err)
      if (err instanceof Error && err.message.includes('토큰 갱신')) {
        setError('로그인이 필요하거나 세션이 만료되었습니다.')
      } else {
        setError('네트워크 오류가 발생했습니다.')
      }
    } finally {
      setLoading(false)
    }
  }

  const fetchStocks = async () => {
    try {
      const response = await fetch('/api/v1/stocks')
      if (response.ok) {
        const result = await response.json()
        setStocks(result.data || [])
      }
    } catch (err) {
      console.error('주식 정보 조회 오류:', err)
    }
  }

  const handleCancelOrder = async (orderId: number) => {
    try {
      setCancelLoading(orderId)
      setMessage('')

      const response = await tokenManager.authenticatedFetch(`/api/v1/orders/${orderId}`, {
        method: 'DELETE'
      })

      if (response.ok) {
        const result = await response.json()
        setMessage(`예약 주문 (ID: ${orderId})이 성공적으로 취소되었습니다.`)
        // 주문 목록 새로고침
        await fetchOrders()
      } else {
        const errorData = await response.json()
        setMessage(errorData.message || `예약 주문 (ID: ${orderId}) 취소 실패!`)
      }
    } catch (err) {
      console.error('주문 취소 오류:', err)
      if (err instanceof Error && err.message.includes('토큰 갱신')) {
        setMessage('로그인이 필요하거나 세션이 만료되었습니다.')
      } else {
        setMessage('네트워크 오류가 발생했습니다.')
      }
    } finally {
      setCancelLoading(null)
    }
  }

  const getStockName = (stockId: number) => {
    const stock = stocks.find(s => s.stockId === stockId)
    return stock ? stock.name : '알 수 없음'
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('ko-KR')
  }

  if (loading) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f8fafc'
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{
            width: 40,
            height: 40,
            border: '4px solid #e5e7eb',
            borderTop: '4px solid #2962FF',
            borderRadius: '50%',
            animation: 'spin 1s linear infinite',
            margin: '0 auto 16px'
          }}></div>
          <p style={{ color: '#6b7280', fontSize: 14 }}>주문 내역을 불러오는 중...</p>
        </div>
        <style>{`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    )
  }

  if (error) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f8fafc'
      }}>
        <div style={{
          width: '100%',
          maxWidth: 400,
          padding: 32,
          background: 'white',
          borderRadius: 12,
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
          margin: 16,
          textAlign: 'center'
        }}>
          <div style={{
            padding: 12,
            background: '#fef2f2',
            border: '1px solid #fecaca',
            borderRadius: 6,
            color: '#dc2626',
            fontSize: 14,
            marginBottom: 16
          }}>
            {error}
          </div>

          <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
            <Link
              to="/login"
              style={{
                padding: '12px 24px',
                background: '#2962FF',
                color: 'white',
                textDecoration: 'none',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: '500',
                display: 'inline-block'
              }}
            >
              로그인하러 가기
            </Link>

            <button
              onClick={() => window.location.reload()}
              style={{
                padding: '12px 24px',
                background: '#6b7280',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: '500',
                cursor: 'pointer'
              }}
            >
              새로고침
            </button>
          </div>
        </div>
      </div>
    )
  }

  const reservedOrders = orders.filter(order => order.reserved)
  const normalOrders = orders.filter(order => !order.reserved)

  return (
    <div style={{
      minHeight: '100vh',
      background: '#f8fafc',
      padding: '20px 0'
    }}>
      <div className="container" style={{
        maxWidth: 1000,
        margin: '0 auto'
      }}>
        {/* 헤더 */}
        <div className="order-header" style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 32
        }}>
          <div className="order-title-group">
            <h1 className="order-title" style={{
              margin: 0,
              fontSize: 28,
              fontWeight: 'bold',
              color: '#1f2937'
            }}>
              주문 관리
            </h1>
            <p className="order-subtitle" style={{
              margin: '8px 0 0 0',
              fontSize: 14,
              color: '#6b7280'
            }}>
              내 주문 내역 조회 및 예약 주문 취소
            </p>
          </div>
          <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
            <Link
              to="/"
              style={{
                color: '#6b7280',
                textDecoration: 'none',
                fontSize: 14,
                whiteSpace: 'nowrap'
              }}
              onMouseOver={(e) => e.currentTarget.style.textDecoration = 'underline'}
              onMouseOut={(e) => e.currentTarget.style.textDecoration = 'none'}
            >
              ← 메인으로 돌아가기
            </Link>
            <Link
              to="/mypage"
              style={{
                color: '#6b7280',
                textDecoration: 'none',
                fontSize: 14,
                whiteSpace: 'nowrap'
              }}
              onMouseOver={(e) => e.currentTarget.style.textDecoration = 'underline'}
              onMouseOut={(e) => e.currentTarget.style.textDecoration = 'none'}
            >
              ← 마이페이지로 돌아가기
            </Link>
          </div>
        </div>

        {/* 메시지 */}
        {message && (
          <div style={{
            padding: 12,
            background: message.includes('성공') ? '#d1fae5' : '#fef2f2',
            border: `1px solid ${message.includes('성공') ? '#34d399' : '#fecaca'}`,
            borderRadius: 6,
            color: message.includes('성공') ? '#065f46' : '#dc2626',
            fontSize: 14,
            marginBottom: 24
          }}>
            {message}
          </div>
        )}

        {/* 예약 주문 내역 */}
        <div className="info-card" style={{
          background: 'white',
          borderRadius: 12,
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
          padding: 24,
          marginBottom: 24
        }}>
          <h2 className="order-section-title" style={{
            margin: '0 0 20px 0',
            fontSize: 20,
            fontWeight: '600',
            color: '#1f2937'
          }}>
            예약 주문 내역 ({reservedOrders.length}건)
          </h2>

          {reservedOrders.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: '40px 20px',
              color: '#6b7280',
              fontSize: 14
            }}>
              예약 주문 내역이 없습니다.
            </div>
          ) : (
            <div className="table-wrapper order-table-wrapper">
              <table className="order-management-table" style={{
                width: '100%',
                borderCollapse: 'collapse',
                fontSize: 14
              }}>
                <thead>
                  <tr style={{
                    background: '#f8fafc',
                    borderBottom: '2px solid #e5e7eb'
                  }}>
                    <th className="col-stock" style={{ padding: '12px 8px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>종목</th>
                    <th className="col-type" style={{ padding: '12px 8px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>구분</th>
                    <th className="col-qty" style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '600', color: '#374151' }}>수량</th>
                    <th style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '600', color: '#374151' }}>목표가</th>
                    <th style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '600', color: '#374151' }}>총 금액</th>
                    <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: '600', color: '#374151' }}>상태</th>
                    <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>주문일시</th>
                    <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: '600', color: '#374151' }}>주문 취소</th>
                  </tr>
                </thead>
                <tbody>
                  {reservedOrders.map(order => (
                    <tr key={order.orderId} style={{
                      borderBottom: '1px solid #f1f5f9'
                    }}>
                      <td className="col-stock" style={{ padding: '12px 8px', fontWeight: '500' }}>{order.stockName}</td>
                      <td className="col-type" style={{ padding: '12px 8px' }}>
                        <span style={{
                          padding: '4px 8px',
                          borderRadius: 4,
                          fontSize: 12,
                          fontWeight: '500',
                          background: order.orderType === 'BUY' ? '#dbeafe' : '#fef2f2',
                          color: order.orderType === 'BUY' ? '#1e40af' : '#dc2626'
                        }}>
                          {order.orderType === 'BUY' ? '매수' : '매도'}
                        </span>
                      </td>
                      <td className="col-qty" style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '500' }}>
                        {order.quantity.toLocaleString()}주
                      </td>
                      <td style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '500' }}>
                        {order.price.toLocaleString()}원
                      </td>
                      <td style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '500' }}>
                        {order.totalPrice.toLocaleString()}원
                      </td>
                      <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                        <span style={{
                          padding: '4px 8px',
                          borderRadius: 4,
                          fontSize: 12,
                          fontWeight: '500',
                          background: order.executed ? '#d1fae5' : '#fef3c7',
                          color: order.executed ? '#065f46' : '#92400e'
                        }}>
                          {order.executed ? '체결완료' : '대기중'}
                        </span>
                      </td>
                      <td style={{ padding: '12px 8px', color: '#6b7280', fontSize: 12 }}>
                        {formatDate(order.createdAt)}
                      </td>
                      <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                        {!order.executed && (
                          <button
                            onClick={() => handleCancelOrder(order.orderId)}
                            disabled={cancelLoading === order.orderId}
                            style={{
                              padding: '6px 12px',
                              background: cancelLoading === order.orderId ? '#9ca3af' : '#ef4444',
                              color: 'white',
                              border: 'none',
                              borderRadius: 6,
                              fontSize: 12,
                              fontWeight: '500',
                              cursor: cancelLoading === order.orderId ? 'not-allowed' : 'pointer',
                              transition: 'all 0.2s'
                            }}
                          >
                            {cancelLoading === order.orderId ? '취소중...' : '취소'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* 일반 주문 내역 */}
        <div className="info-card" style={{
          background: 'white',
          borderRadius: 12,
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
          padding: 24
        }}>
          <h2 className="order-section-title" style={{
            margin: '0 0 20px 0',
            fontSize: 20,
            fontWeight: '600',
            color: '#1f2937'
          }}>
            일반 주문 내역 ({normalOrders.length}건)
          </h2>

          {normalOrders.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: '40px 20px',
              color: '#6b7280',
              fontSize: 14
            }}>
              일반 주문 내역이 없습니다.
            </div>
          ) : (
            <div className="table-wrapper order-table-wrapper">
              <table className="order-management-table" style={{
                width: '100%',
                borderCollapse: 'collapse',
                fontSize: 14
              }}>
                <thead>
                  <tr style={{
                    background: '#f8fafc',
                    borderBottom: '2px solid #e5e7eb'
                  }}>
                    <th className="col-stock" style={{ padding: '12px 8px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>종목</th>
                    <th className="col-type" style={{ padding: '12px 8px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>구분</th>
                    <th className="col-qty" style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '600', color: '#374151' }}>수량</th>
                    <th style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '600', color: '#374151' }}>체결가</th>
                    <th style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '600', color: '#374151' }}>총 금액</th>
                    <th style={{ padding: '12px 8px', textAlign: 'center', fontWeight: '600', color: '#374151' }}>상태</th>
                    <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: '600', color: '#374151' }}>주문일시</th>
                  </tr>
                </thead>
                <tbody>
                  {normalOrders.map(order => (
                    <tr key={order.orderId} style={{
                      borderBottom: '1px solid #f1f5f9'
                    }}>
                      <td className="col-stock" style={{ padding: '12px 8px', fontWeight: '500' }}>{order.stockName}</td>
                      <td className="col-type" style={{ padding: '12px 8px' }}>
                        <span style={{
                          padding: '4px 8px',
                          borderRadius: 4,
                          fontSize: 12,
                          fontWeight: '500',
                          background: order.orderType === 'BUY' ? '#dbeafe' : '#fef2f2',
                          color: order.orderType === 'BUY' ? '#1e40af' : '#dc2626'
                        }}>
                          {order.orderType === 'BUY' ? '매수' : '매도'}
                        </span>
                      </td>
                      <td className="col-qty" style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '500' }}>
                        {order.quantity.toLocaleString()}주
                      </td>
                      <td style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '500' }}>
                        {order.price.toLocaleString()}원
                      </td>
                      <td style={{ padding: '12px 8px', textAlign: 'right', fontWeight: '500' }}>
                        {order.totalPrice.toLocaleString()}원
                      </td>
                      <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                        <span style={{
                          padding: '4px 8px',
                          borderRadius: 4,
                          fontSize: 12,
                          fontWeight: '500',
                          background: order.executed ? '#d1fae5' : '#fef3c7',
                          color: order.executed ? '#065f46' : '#92400e'
                        }}>
                          {order.executed ? '체결완료' : '대기중'}
                        </span>
                      </td>
                      <td style={{ padding: '12px 8px', color: '#6b7280', fontSize: 12 }}>
                        {formatDate(order.createdAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
