import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import Home from './pages/Home'
import Detail from './pages/Detail'

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/stocks/:ticker', element: <Detail /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>
)



