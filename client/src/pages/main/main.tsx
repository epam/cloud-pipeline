import {lazy, Suspense} from 'react';
import {QueryClientProvider} from '@tanstack/react-query';
import {ReactQueryDevtools} from '@tanstack/react-query-devtools';
import {ErrorBoundary} from '../../components/shared/error-boundary/error-boundary';
import {LoadingPage} from './loading.tsx';
import {ThemeProvider} from '../../stores/themes';
import {PageSuspense} from '../layouts/page-suspense.tsx';
import {queryClient} from '../../queries/query-client.ts';
import {AppRouter} from './app-router.tsx';

function Main() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider className="h-full w-full">
          <LoadingPage className="h-full w-full overflow-auto bg-app-layout">
            <PageSuspense>
              <AppRouter />
            </PageSuspense>
          </LoadingPage>
        </ThemeProvider>
        {DEVELOPMENT ? <ReactQueryDevtools initialIsOpen={false} /> : null}
      </QueryClientProvider>
    </ErrorBoundary>
  );
}

export {Main};
