import { useEffect, useRef } from 'react'
import { createStompClient } from '../lib/socket'

/**
 * 전역 주문 체결 알림 WebSocket 컴포넌트
 * 로그인 사용자에게만 주문 체결 알림을 전송
 */
export default function GlobalNotification() {
  const clientRef = useRef<any>(null)

  useEffect(() => {
    console.log('전역 알림 WebSocket 컴포넌트 마운트됨')
    
    // 로그인 상태 확인
    const userInfoStr = localStorage.getItem('userInfo')
    if (!userInfoStr) {
      console.log('로그인되지 않음 - WebSocket 연결 안 함')
      return
    }

    try {
      const userInfo = JSON.parse(userInfoStr)
      const userId = userInfo?.userId
      
      if (!userId) {
        console.log('사용자 ID 없음 - WebSocket 연결 안 함')
        return
      }

      console.log('사용자 ID:', userId, '- WebSocket 연결 시작')
      
      // 브라우저 알림 권한 요청 및 테스트
      if ('Notification' in window) {
        console.log('현재 알림 권한 상태:', Notification.permission)
        if (Notification.permission === 'default') {
          Notification.requestPermission().then((permission) => {
            console.log('알림 권한 요청 결과:', permission)
            if (permission === 'granted') {
              // 테스트 알림 표시
              try {
                const testNotification = new Notification('알림 테스트', {
                  body: '주문 알림이 정상적으로 작동합니다.'
                })
                testNotification.onclick = () => testNotification.close()
                setTimeout(() => testNotification.close(), 3000)
              } catch (e) {
                console.error('테스트 알림 실패:', e)
              }
            }
          })
        } else if (Notification.permission === 'granted') {
          console.log('알림 권한이 이미 허용되어 있습니다')
        } else {
          console.log('알림 권한이 거부되어 있습니다')
        }
      } else {
        console.log('브라우저가 Notification API를 지원하지 않습니다')
      }

      // WebSocket 연결
      const client = createStompClient(() => {
        console.log('전역 알림 WebSocket 메시지 수신')
      }, () => {
        console.log('전역 주문 알림 WebSocket 연결 성공')
        
        // 주문 알림 구독
        const subscriptionTopic = `/topic/order/notifications/${userId}`
        console.log('주문 알림 토픽 구독:', subscriptionTopic)
        client.subscribe(subscriptionTopic, (msg: any) => {
          console.log('=== 주문 알림 WebSocket 메시지 수신 ===')
          console.log('원본 데이터:', msg.body)
          try {
            const notificationData = JSON.parse(msg.body)
            if ('Notification' in window && Notification.permission === 'granted') {
              try {
                new Notification('주문 체결 알림', {
                  body: notificationData.message || '주문이 체결되었습니다.',
                  tag: `order-${userId}-${Date.now()}`
                })
              } catch (notificationError) {
                console.error('알림 생성 중 예외 발생:', notificationError)
              }
            }
          } catch (error) {
            console.error('주문 알림 파싱 오류:', error)
          }
        })
      })
      
      client.onStompError = (frame) => {
        console.error('WebSocket STOMP 에러:', frame)
      }
      
      client.activate()
      clientRef.current = client
      
    } catch (error) {
      console.error('전역 알림 WebSocket 설정 오류:', error)
    }

    // 컴포넌트 언마운트 시 WebSocket 연결 해제
    return () => {
      if (clientRef.current) {
        console.log('전역 알림 WebSocket 연결 해제')
        clientRef.current.deactivate()
      }
    }
  }, [])

  return null // UI를 렌더링하지 않음
}

