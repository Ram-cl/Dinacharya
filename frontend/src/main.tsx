import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { ToastContainer, toast } from 'react-toastify'
import { registerSW } from 'virtual:pwa-register'
import App from './App'
import './index.css'
import 'react-toastify/dist/ReactToastify.css'

// Register service worker with update notification
registerSW({
  onNeedRefresh() {
    toast.info(
      () => (
        <div className="flex items-center gap-3">
          <span>New version available.</span>
          <button
            className="underline font-medium"
            onClick={() => window.location.reload()}
          >
            Refresh
          </button>
        </div>
      ),
      { autoClose: false, closeOnClick: false }
    )
  },
  onOfflineReady() {
    toast.success('App is ready to work offline.', { autoClose: 3000 })
  },
})

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30000,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
        <ToastContainer position="top-right" autoClose={3000} />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
)
