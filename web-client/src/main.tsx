import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { App } from './App';
import { ToastProvider } from './components/Toast';
import { ApiError } from './api/client';
import { restoreSession } from './api/session';
import './styles/app.css';

// Rehydrate the access token before the first render so a refresh doesn't
// bounce a signed-in user back to the login screen.
restoreSession();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Diary data changes only when this user changes it, so a short stale
      // window plus explicit invalidation after mutations is enough.
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Auth failures and "not found" will never succeed on retry.
        if (error instanceof ApiError && (error.isAuthFailure || error.status === 404)) return false;
        return failureCount < 2;
      },
    },
    mutations: { retry: false },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ToastProvider>
          <App />
        </ToastProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
