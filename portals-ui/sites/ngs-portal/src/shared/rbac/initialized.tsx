import type { FunctionComponent, ReactNode } from 'react';
import { useApplicationInitialized } from '../../state/initialization/hooks.ts';

export function Initialized(props: { children?: ReactNode }) {
  const { children } = props;
  const initialized = useApplicationInitialized();
  if (initialized) {
    return children;
  }
  return null;
}

export function initialized<Props extends {}>(
  Component: FunctionComponent<Props>,
): FunctionComponent<Props> {
  function _(props: Props) {
    return (
      <Initialized>
        <Component {...props} />
      </Initialized>
    );
  }
  _.disaplayName = Component.displayName;
  return _;
}
