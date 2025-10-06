import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import Home from './pages/Home'
import Detail from './pages/Detail'
import Chart from './pages/Chart.jsx'

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/stocks/:ticker', element: <Detail /> },
  { path: '/stocks/:ticker/chart', element: <Chart /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>
)



