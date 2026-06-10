import type {ReactNode} from 'react';
import classNames from 'classnames';
import type {CommonProps} from '../../../@types/common.ts';
import './loading-message.css';

export function LoadingMessage(
  props: CommonProps & {
    loading?: boolean;
    children?: ReactNode;
  },
) {
  const {className, style, children, loading = true} = props;
  if (!children) {
    return null;
  }
  if (!loading) {
    return (
      <span className={className} style={style}>
        {children}
      </span>
    );
  }
  return (
    <span
      className={classNames('loading-message', className)}
      style={style}
      role="status"
      aria-live="polite"
    >
      {children}
    </span>
  );
}
