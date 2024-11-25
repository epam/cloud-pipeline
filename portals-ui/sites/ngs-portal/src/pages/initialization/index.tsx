import type { CommonParentProps } from '@cloud-pipeline/components';
import { useAuthenticationState } from '../../state/authentication/hooks.ts';
import { useInitializeApplication } from '../../state/initialization/hooks.ts';
import './style.css';

export default function Initialization(props: CommonParentProps) {
  const { children } = props;
  const {
    error: authenticationError,
    authenticatedUser,
    pending: authPending,
  } = useAuthenticationState();
  const {
    completed: initialized,
    error: initializationError,
    pending: initializePending,
  } = useInitializeApplication();

  if (authenticatedUser && initialized) {
    return children;
  }

  if (authenticationError || initializationError) {
    return (
      <div className="app-initialization">
        <span className="auth-error">
          {authenticationError ?? initializationError}
        </span>
      </div>
    );
  }
  if (authPending || initializePending) {
    return (
      <div className="app-initialization">
        <span className="auth-pending">Loading...</span>
      </div>
    );
  }
  return (
    <div className="app-initialization">
      <span className="auth-error">Something went wrong.</span>
    </div>
  );
}
