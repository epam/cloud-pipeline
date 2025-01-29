import type { CommonParentProps } from '@cloud-pipeline/components';
import { useInitializationStore } from '../../state/initialization/hooks.ts';
import './style.css';
import { useMemo } from 'react';

export default function Initialization(props: CommonParentProps) {
  const { children } = props;
  const {
    data: initialized,
    error,
    pending,
    messages,
  } = useInitializationStore();

  const lastMessage = useMemo(
    () =>
      messages.length > 0
        ? messages[messages.length - 1]
        : {
            message: 'Loading...',
          },
    [messages],
  );

  if (initialized) {
    return children;
  }

  if (error) {
    return (
      <div className="app-initialization">
        <span className="app-initialization-error">{error}</span>
      </div>
    );
  }
  if (pending) {
    return (
      <div className="app-initialization">
        <div className="text-center">
          <div className="app-initialization-pending">
            {lastMessage.message}
          </div>
          {lastMessage.details && (
            <div className="app-initialization-pending-details">
              {lastMessage.details}
            </div>
          )}
        </div>
      </div>
    );
  }
  return (
    <div className="app-initialization">
      <span className="app-initialization-error">Something went wrong.</span>
    </div>
  );
}
