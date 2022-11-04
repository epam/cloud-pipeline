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
import {Provider, inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import {RunOperationWarningAlert} from './warning';

let storeIndex = 0;

function asyncFunctionCall (fn, ...options) {
  if (typeof fn === 'function') {
    const call = fn(...options);
    if (call && typeof call.then === 'function') {
      return new Promise((resolve, reject) => {
        call
          .then(resolve)
          .catch(reject);
      });
    }
    return call;
  }
  return Promise.resolve();
}

/**
 * @typedef {Object} RunOperationCheckProviderOptions
 * @property {function(runId:number):Promise<boolean>} check
 * @property {React.ReactNode|function} warning
 * @property {string} [storeName]
 */

/**
 * @typedef {function(*):boolean} getCheckResultFn
 */

/**
 * @typedef {function(*):{pending:boolean,checkResult:{result: boolean?}}} getCheckInfoFn
 */

/**
 * @typedef {Object} RunOperationCheckProviderType
 * @property {string} store
 * @property {function} inject
 * @property {React.ComponentClass} Warning
 * @property {function(props:*):{pending:boolean,checkResult:{result:boolean}}} getCheckInfo
 * @property {function(props:*):boolean} getCheckResult
 */

/**
 * @typedef {Object} RunCheckOperationGenerator
 * @property {getCheckResultFn} getCheckResult
 * @property {getCheckInfoFn} getCheckInfo
 * @property {React.ComponentClass & RunOperationCheckProviderType} RunOperationCheckProvider
 * @property {function} RunOperationCheckWarning
 */

/**
 * @param {RunOperationCheckProviderOptions} options
 * @returns {React.ComponentClass & RunOperationCheckProviderType}
 */
export default function generateProvider (options) {
  storeIndex += 1;
  const {
    check,
    warning,
    storeName = `checkRunStore${storeIndex}`
  } = options || {};
  /**
   * @type {React.ComponentClass & RunOperationCheckProviderType}
   */
  const RunOperationCheckProvider = class extends React.PureComponent {
    @observable storeObj = {
      pending: false,
      checkResult: {result: false},
      runId: undefined
    };

    componentDidMount () {
      this.checkRun();
    }

    componentDidUpdate (prevProps, prevState, snapshot) {
      if (
        (
          prevProps.runId !== this.props.runId ||
          prevProps.active !== this.props.active
        ) &&
        this.props.active
      ) {
        this.checkRun();
      }
    }

    checkRun () {
      const {active, runId} = this.props;
      this.storeObj.runId = runId;
      if (active && runId && typeof check === 'function') {
        this.storeObj.pending = true;
        this.storeObj.checkResult = {result: false};
        asyncFunctionCall(check, runId)
          .catch(() => Promise.resolve(false))
          .then(result => {
            if (this.storeObj.runId === runId) {
              this.storeObj.pending = false;
              this.storeObj.checkResult = result;
            }
          });
      } else {
        this.storeObj.pending = false;
        this.storeObj.checkResult = {result: false};
      }
    }

    render () {
      const {
        children
      } = this.props;
      return (
        <Provider {...{[storeName]: this.storeObj}}>
          {children}
        </Provider>
      );
    }
  };

  RunOperationCheckProvider.propTypes = {
    runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    children: PropTypes.node,
    active: PropTypes.bool
  };

  RunOperationCheckProvider.defaultProps = {
    active: true
  };

  function getCheckInfo (props) {
    const {
      [storeName]: storeObj
    } = props || {};
    return storeObj || {};
  }

  function getCheckResult (props) {
    const {
      pending,
      checkResult = {}
    } = getCheckInfo(props);
    const {
      result
    } = checkResult;
    return pending ? false : result;
  }

  function Warning (props) {
    const {
      className,
      style,
      type = 'error'
    } = props;
    const {
      pending,
      checkResult = {}
    } = getCheckInfo(props);
    const {
      result
    } = checkResult;
    if (pending || result) {
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

  const WrappedWarning = inject(storeName)(observer(Warning));
  WrappedWarning.propTypes = {
    className: PropTypes.string,
    style: PropTypes.object,
    type: PropTypes.string
  };
  WrappedWarning.defaultProps = {
    type: 'error'
  };

  RunOperationCheckProvider.store = storeName;
  RunOperationCheckProvider.inject = (...opts) => inject(storeName)(...opts);
  RunOperationCheckProvider.Warning = WrappedWarning;
  RunOperationCheckProvider.getCheckInfo = getCheckInfo;
  RunOperationCheckProvider.getCheckResult = getCheckResult;

  return RunOperationCheckProvider;
}
