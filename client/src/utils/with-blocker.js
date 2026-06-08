import React, { useState } from 'react';
import { useBlocker } from 'react-router-dom';

/**
 * HOC that wraps a component with useBlocker from react-router-dom.
 * The wrapped component receives:
 *   - `blocker` prop  (state: 'unblocked' | 'blocked' | 'proceeding', proceed(), reset())
 *   - `setNavigationBlocked(bool)` — call this to enable/disable navigation blocking
 */
export function withBlocker(Component) {
  function Wrapper(props) {
    const [shouldBlock, setShouldBlock] = useState(false);
    const blocker = useBlocker(shouldBlock);
    return <Component {...props} blocker={blocker} setNavigationBlocked={setShouldBlock} />;
  }
  Wrapper.displayName = `WithBlocker(${Component.displayName || Component.name || 'Component'})`;
  return Wrapper;
}
