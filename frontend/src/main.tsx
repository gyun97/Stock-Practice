import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import Home from './pages/Home'
import Detail from './pages/Detail'
import Chart from './pages/Chart.jsx'
import Login from './pages/Login'
import SignUp from './pages/SignUp'

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/stocks/:ticker', element: <Detail /> },
  { path: '/stocks/:ticker/chart', element: <Chart /> },
  { path: '/login', element: <Login /> },
  { path: '/signup', element: <SignUp /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>
)



