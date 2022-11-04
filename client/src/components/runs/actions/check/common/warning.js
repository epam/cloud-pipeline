/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Alert} from 'antd';
import classNames from 'classnames';
import styles from './warning.css';

function wrapWarningProp (warning) {
  if (typeof warning === 'function') {
    return warning;
  }
  return () => warning;
}

function RunOperationWarningAlert (
  {
    className,
    style,
    type,
    message,
    checkResult
  }
) {
  return (
    <Alert
      className={
        classNames(
          className,
          styles.alert
        )
      }
      style={style}
      type={type}
      message={wrapWarningProp(message)(checkResult)}
    />
  );
}

class RunOperationWarning extends React.PureComponent {
  state = {
    pending: false,
    checkResult: {result: false}
  };

  componentDidMount () {
    this.checkRun();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.runId !== this.props.runId ||
      prevProps.check !== this.props.check
    ) {
      this.checkRun();
    }
  }

  checkRun = () => {
    const {runId, check} = this.props;
    if (runId && typeof check === 'function') {
      this.setState({
        pending: true,
        checkResult: {result: false}
      }, () => {
        check(runId)
          .then(result => this.setState({
            pending: false,
            checkResult: result
          }));
      });
    } else {
      this.setState({
        pending: false,
        checkResult: {result: false}
      });
    }
  };

  render () {
    const {
      className,
      style,
      runId,
      type = 'error',
      warning
    } = this.props;
    const {
      pending,
      checkResult
    } = this.state;
    if (!runId || pending) {
      return null;
    }
    const {
      result
    } = checkResult || {};
    if (result) {
      return null;
    }
    return (
      <RunOperationWarningAlert
        className={className}
        style={style}
        type={type}
        message={warning}
        checkResult={checkResult}
      />
    );
  }
}

RunOperationWarning.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  type: PropTypes.string,
  check: PropTypes.func,
  warning: PropTypes.oneOfType([PropTypes.func, PropTypes.node])
};

export {RunOperationWarningAlert};
export default RunOperationWarning;
