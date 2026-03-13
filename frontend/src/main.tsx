import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import Home from './pages/Home'
import Detail from './pages/Detail'
// @ts-ignore
import Chart from './pages/Chart.jsx'
import Login from './pages/Login'
import MyPage from './pages/MyPage'
import OrderManagement from './pages/OrderManagement'
import GlobalNotification from './components/GlobalNotification'
import { tokenManager } from './lib/tokenManager'
import './Responsive.css'

// 전역 fetch 인터셉터 - 401 응답 시 자동 토큰 재발급
export const originalFetch = window.fetch;
(window as any).__originalFetch = originalFetch; // 전역 인터셉터 우회를 위해 저장

window.fetch = async function (...args): Promise<Response> {
  let [url, options = {}] = args
  const headers = options.headers as HeadersInit || {}

  // API 요청이면 Authorization 헤더 자동 추가
  const urlString = typeof url === 'string' ? url : url.toString()
  if (urlString.startsWith('/api/')) {
    const token = tokenManager.getAccessToken() // 메모리에서 토큰 가져오기
    if (token) {
      const newHeaders = new Headers(headers)
      newHeaders.set('Authorization', `Bearer ${token}`)
      options.headers = newHeaders
    }
  }

  const response = await originalFetch(url as string, options)

  // 401 응답이면 토큰 재발급 시도 (API 요청인 경우만)
  if (response.status === 401 && urlString.startsWith('/api/')) {
    console.log('401 응답 감지, 토큰 재발급 시도')

    try {
      const newAccessToken = await tokenManager.refreshAccessToken()

      // 재발급 성공 시 원본 요청 재시도
      const newHeaders = new Headers(options.headers as HeadersInit || {})
      newHeaders.set('Authorization', `Bearer ${newAccessToken}`)
      const newOptions = { ...options, headers: newHeaders }

      return await originalFetch(url as string, newOptions)
    } catch (error) {
      console.error('토큰 재발급 실패:', error)
      // 토큰 재발급 실패해도 원본 응답 반환 (페이지에서 처리하도록)
      return response
    }
  }

  return response
}

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/stocks/:ticker', element: <Detail /> },
  { path: '/stocks/:ticker/chart', element: <Chart /> },
  { path: '/login', element: <Login /> },
  { path: '/mypage', element: <MyPage /> },
  { path: '/order-management', element: <OrderManagement /> },
])

const rootElement = document.getElementById('root')!

createRoot(rootElement).render(
  <StrictMode>
    <GlobalNotification />
    <RouterProvider router={router} />
  </StrictMode>
)



