import {Suspense, type ReactNode} from 'react';
import {LoadingMessage} from '../../components/shared/loading-message/loading-message.tsx';

function PageSuspense({children}: {children: ReactNode}) {
  return (
    <Suspense
      fallback={
        <div className="flex h-full items-center justify-center">
          <LoadingMessage>Loading...</LoadingMessage>
        </div>
      }
    >
      {children}
    </Suspense>
  );
}

export {PageSuspense};
