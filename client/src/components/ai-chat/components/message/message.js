/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import {computed, makeObservable} from 'mobx';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import {Alert} from 'antd';
import {TypingIndicator} from '../index';
import LaunchForm from '../launch-form/launch-form';
import Markdown from '../../../special/markdown';
import styles from './message.css';
import RunStatusMessage from './run-status-message';
import MessagePart from './message-part';

const showContextData = false;

@observer
export default class Message extends React.Component {
  constructor (props) {
    super(props);
    makeObservable(this, {
      message: computed,
      pending: computed
    });
  }

  get message () {
    return this.props.message;
  }

  get pending () {
    return this.props.message.pending;
  }

  handleRunSuccess = (run) => {
    const {onRunLaunchSuccess} = this.props;
    if (onRunLaunchSuccess) {
      onRunLaunchSuccess(run);
    }
  };

  renderMessagePart = (part, message) => {
    const {
      role = 'user',
      pending = false
    } = message;
    const assistantMessage = role.toLowerCase() === 'assistant';
    const {
      identifier,
      text,
      type = 'text',
      data = {},
      errors = [],
      warnings = [],
      status
    } = part;
    const contentParts = [];
    if (type.toLowerCase() === 'launch') {
      const {
        launch_payload: launchPayload
      } = data;
      if (launchPayload) {
        contentParts.push(
          <LaunchForm
            key={`${identifier}-launch`}
            data={launchPayload}
            onRunSuccess={this.handleRunSuccess}
          />
        );
      }
    }
    if (type.toLowerCase() === 'run') {
      const {
        run_payload: runPayload
      } = data;
      if (runPayload) {
        contentParts.push(
          <RunStatusMessage
            key={`${identifier}-run`}
            run={runPayload}
          />
        );
      }
    }
    if (text && text.length > 0) {
      if (type.toLowerCase() === 'context') {
        if (showContextData) {
          contentParts.push(
            <span
              key={`${identifier}-${type}-text`}
              className="cp-text-not-important"
              style={{fontSize: 'smaller'}}
            >
              {text}
            </span>
          );
        }
      } else {
        contentParts.push(
          (<MessagePart
            key={`${identifier}-${type}-text`}
            text={text}
            alive={assistantMessage && pending}
          />)
        );
      }
    }
    if (errors.length > 0) {
      contentParts.push(
        <Alert
          key={`${identifier}-errors`}
          message={(
            <div>
              {errors.map((err, idx) => (<Markdown key={`error-${idx}`} md={err} />))}
            </div>
          )}
          showIcon
          type="error"
        />
      );
    }
    if (warnings.length > 0) {
      contentParts.push(
        <Alert
          key={`${identifier}-warnings`}
          message={(
            <div>
              {warnings.map((warn, idx) => (<Markdown key={`error-${idx}`} md={warn} />))}
            </div>
          )}
          showIcon
          type="warning"
        />
      );
    }
    if (status) {
      contentParts.push(
        <div
          key={`${identifier}-status`}
          className="cp-text-not-important"
          style={{fontSize: 'smaller'}}
        >
          {status}
        </div>
      );
    }
    if (contentParts.length === 0) {
      return null;
    }
    return (
      <div key={part.identifier} className={styles.messagePart}>
        {contentParts}
      </div>
    );
  };

  renderContent = () => {
    const {message} = this;
    const {
      identifier,
      parts = [],
      pending = false,
      status
    } = message;
    return (
      <div key={identifier} style={{width: '100%'}} className={styles.messageParts}>
        {
          parts.map((part) => this.renderMessagePart(part, message))
        }
        {
          pending && (
            <div
              className="cp-text-not-important"
              style={{fontSize: 'smaller', minHeight: 24}}
            >
              {status}
            </div>
          )
        }
        {
          pending && (
            <div style={{width: '100%'}}>
              <TypingIndicator />
            </div>
          )
        }
      </div>
    );
  };

  render () {
    const {
      className,
      style,
      message,
      first,
      last
    } = this.props;
    const {
      role = 'user'
    } = message;
    const userMessage = role.toLowerCase() === 'user';
    return (
      <div
        className={classNames(
          className,
          'cp-panel',
          'cp-panel-no-hover',
          styles.message,
          {
            [styles.first]: first,
            [styles.last]: last,
            [styles.user]: userMessage,
            'cp-panel-borderless': userMessage,
            'cp-bordered': !userMessage
          }
        )}
        style={{...(style || {}), ...(userMessage ? {} : {width: '100%'})}}
      >
        {this.renderContent()}
      </div>
    );
  }
}

Message.propTypes = {
  message: PropTypes.object,
  className: PropTypes.string,
  style: PropTypes.object,
  first: PropTypes.bool,
  last: PropTypes.bool,
  onRunLaunchSuccess: PropTypes.func
};
