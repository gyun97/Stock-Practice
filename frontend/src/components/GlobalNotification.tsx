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
      })
      
      client.onConnect = () => {
        console.log('전역 주문 알림 WebSocket 연결 성공')
        
        // 주문 알림 구독
        const subscriptionTopic = `/topic/order/notifications/${userId}`
        console.log('주문 알림 토픽 구독:', subscriptionTopic)
        client.subscribe(subscriptionTopic, (msg) => {
          console.log('=== 주문 알림 WebSocket 메시지 수신 ===')
          console.log('원본 메시지:', msg.body)
          console.log('메시지 타입:', typeof msg.body)
          try {
            const notificationData = JSON.parse(msg.body)
            console.log('전역 주문 알림 수신 (파싱 성공):', notificationData)
            
            // 브라우저 알림 권한 상태 확인
            console.log('브라우저 알림 권한:', Notification.permission)
            
            // 브라우저 알림 표시
            if ('Notification' in window && Notification.permission === 'granted') {
              console.log('브라우저 알림 표시 중...')
              console.log('브라우저 정보:', {
                userAgent: navigator.userAgent,
                platform: navigator.platform,
                documentHasFocus: document.hasFocus(),
                windowFocused: document.visibilityState === 'visible'
              })
              
              try {
                // 최소한의 옵션만으로 알림 생성 (가능한 모든 옵션 제거)
                const notification = new Notification('주문 체결 알림', {
                  body: notificationData.message || '주문이 체결되었습니다.',
                  tag: `order-${userId}-${Date.now()}` // 사용자별로 구분
                })
                
                console.log('Notification 객체 생성됨:', {
                  title: notification.title,
                  body: notification.body,
                  tag: notification.tag
                })
                
                // 알림 이벤트 핸들러 (모든 이벤트 로깅)
                notification.onclick = () => {
                  console.log('알림 클릭됨')
                  window.focus()
                  notification.close()
                }
                
                notification.onshow = () => {
                  console.log('알림 onshow 이벤트 발생 - 알림이 표시되었습니다!')
                  // 3초 후 확인
                  setTimeout(() => {
                    console.log('알림 상태 확인 (3초 후):', {
                      title: notification.title,
                      body: notification.body
                    })
                  }, 3000)
                }
                
                notification.onclose = () => {
                  console.log('알림 onclose 이벤트 발생 - 알림이 닫혔습니다')
                }
                
                notification.onerror = (error: Event) => {
                  console.error('알림 onerror 이벤트 발생:', error)
                  console.error('에러 타입:', error?.type)
                }

              } catch (notificationError: any) {
                console.error('알림 생성 중 예외 발생:', notificationError)
                console.error('예외 상세:', {
                  name: notificationError?.name || 'Unknown',
                  message: notificationError?.message || String(notificationError),
                  stack: notificationError?.stack || 'No stack trace'
                })
              }
              
            } else if (Notification.permission === 'denied') {
              console.log('브라우저 알림 권한이 거부되었습니다. 브라우저 설정에서 알림을 허용해주세요.')
              // 권한이 거부된 경우에도 사용자에게 알림 표시 (페이지 내)
              if (window.confirm(`주문 체결 알림: ${notificationData.message}\n\n브라우저 알림 권한을 허용하면 알림을 받을 수 있습니다.`)) {
                // 사용자가 확인을 누르면 창 포커스
                window.focus()
              }
            } else if (Notification.permission === 'default') {
              console.log('브라우저 알림 권한을 요청해야 합니다.')
              // 권한 요청
              Notification.requestPermission().then((permission) => {
                console.log('알림 권한 결과:', permission)
                if (permission === 'granted') {
                  console.log('알림 권한이 허용되었습니다. 다음 알림부터 표시됩니다.')
                }
              })
            }
          } catch (error) {
            console.error('주문 알림 파싱 오류:', error)
          }
        })
      }
      
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

