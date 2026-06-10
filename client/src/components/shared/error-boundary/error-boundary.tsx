import React, {type ReactNode} from 'react';
import {Button} from 'antd';
import {WarningOutlined} from '@ant-design/icons';

type ErrorBoundaryState = {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
};

type ErrorBoundaryProps = {
  children: ReactNode;
};

class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = {
    hasError: false,
    error: null,
    errorInfo: null,
  };

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return {hasError: true, error};
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    this.setState({errorInfo});
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  onReload = () => {
    this.setState({hasError: false, error: null, errorInfo: null});
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex h-full flex-col items-center justify-center p-10 text-center">
          <WarningOutlined className="mb-4 text-5xl text-red-500" />
          <h2 className="mb-2 text-xl font-semibold">Something went wrong</h2>
          <p className="mb-6 max-w-lg text-neutral-500">
            An unexpected error occurred. You can try to recover by clicking the button below, or
            reload the page.
          </p>
          <div>
            <Button type="primary" onClick={this.onReload} className="mr-2">
              Try to recover
            </Button>
            <Button onClick={() => window.location.reload()}>Reload page</Button>
          </div>
          {this.state.error ? (
            <pre className="mt-6 max-h-52 max-w-full overflow-auto rounded bg-neutral-100 p-4 text-left text-xs text-neutral-500">
              {this.state.error.toString()}
              {this.state.errorInfo?.componentStack}
            </pre>
          ) : null}
        </div>
      );
    }

    return this.props.children;
  }
}

export {ErrorBoundary};
