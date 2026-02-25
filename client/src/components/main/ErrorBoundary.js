/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {Button} from 'antd';
import {WarningOutlined} from '@ant-design/icons';

class ErrorBoundary extends React.Component {
  state = {
    hasError: false,
    error: null,
    errorInfo: null
  };

  static getDerivedStateFromError (error) {
    return {hasError: true, error};
  }

  componentDidCatch (error, errorInfo) {
    this.setState({errorInfo});
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  onReload = () => {
    this.setState({hasError: false, error: null, errorInfo: null});
  };

  render () {
    if (this.state.hasError) {
      return (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100%',
          padding: 40,
          textAlign: 'center'
        }}>
          <WarningOutlined style={{fontSize: 48, color: '#f5222d', marginBottom: 16}} />
          <h2 style={{marginBottom: 8}}>Something went wrong</h2>
          <p style={{color: '#666', marginBottom: 24, maxWidth: 500}}>
            An unexpected error occurred. You can try to recover by clicking
            the button below, or reload the page.
          </p>
          <div>
            <Button
              type="primary"
              onClick={this.onReload}
              style={{marginRight: 8}}
            >
              Try to recover
            </Button>
            <Button onClick={() => window.location.reload()}>
              Reload page
            </Button>
          </div>
          {this.state.error && (
            <pre style={{
              marginTop: 24,
              padding: 16,
              background: '#f5f5f5',
              borderRadius: 4,
              maxWidth: '100%',
              overflow: 'auto',
              textAlign: 'left',
              fontSize: 12,
              color: '#999',
              maxHeight: 200
            }}>
              {this.state.error.toString()}
              {this.state.errorInfo && this.state.errorInfo.componentStack}
            </pre>
          )}
        </div>
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
