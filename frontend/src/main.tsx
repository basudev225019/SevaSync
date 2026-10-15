import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import '@fontsource-variable/inter';
import '@fontsource-variable/space-grotesk';
import '@fontsource/jetbrains-mono/400.css';
import './index.css';
import { AuthProvider } from './context/AuthContext';
import App from './App';
import { ApiError } from './services/api';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10_000,
      refetchOnWindowFocus: true,
      // don't hammer the server for errors a retry cannot fix
      retry: (count, error) => !(error instanceof ApiError && [401, 403, 404].includes(error.status)) && count < 2,
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <App />
        <Toaster position="bottom-right" theme="light" richColors closeButton />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
