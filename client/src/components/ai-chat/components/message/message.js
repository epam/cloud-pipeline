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
import {computed} from 'mobx';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import {Link} from 'react-router';
import {TypingIndicator} from '../index';
import LaunchForm from '../launch-form/launch-form';
import Markdown from '../../../special/markdown';
import {processMessage} from './message-utils';
import styles from './message.css';
import StatusIcon from '../../../special/run-status-icon';

const mockParameters = (value) => {
  const parameters = {
    Reference_Path: {type: 'string', value: 'Some value'},
    Boolean_Type_Parameter: {type: 'boolean', value: true},
    Path_To_File: {type: 'path', value: '/path/to/file'},
    Another_Path_Parameter: {type: 'path', value: '/another/path'}
  };
  if (!value.parameters || Object.keys(value.parameters).length === 0) {
    return {
      ...value,
      pipelineId: 1966,
      parameters
    };
  }
  return value;
};

@observer
export default class Message extends React.Component {
  @computed
  get message () {
    return this.props.message
      ? processMessage(this.props.message)
      : undefined;
  }

  @computed
  get pending () {
    return this.props.message.pending;
  }

  handleRunSuccess = (run) => {
    const {onRunLaunchSuccess} = this.props;
    if (onRunLaunchSuccess) {
      onRunLaunchSuccess(run);
    }
  };

  renderRunStatusMessage = () => {
    const {aclClass = '', name, taskName} = this.message.run;
    const runType = aclClass.toLowerCase();
    const runName = runType === 'pipeline'
      ? `${name || taskName}-${this.message.run.id}`
      : name || taskName;
    return (
      <div style={{display: 'flex', alignItems: 'center', gap: '5px'}}>
        <StatusIcon
          run={this.message.run}
          small
          status="RUNNING"
        />
        <span style={{textTransform: 'capitalize'}}>{runType}</span>
        <Link to={`run/${this.message.run.id}`}>
          {runName}
        </Link>
        was successfully launched!
      </div>
    );
  };

  renderContent = () => {
    if (this.message.fromUser) {
      return <span style={{whiteSpace: 'pre-line'}}>{this.message.text}</span>;
    }
    if (this.message.error) {
      return (
        <p className={styles.errorConnect}>
          Error: {this.message.error}
        </p>
      );
    }
    if (!this.message.fromUser && this.message.run) {
      return this.renderRunStatusMessage();
    }
    if (this.message.parts?.length > 0) {
      return (
        <div>
          {this.message.parts.map((part, index) => {
            if (part.isText && part.value) {
              return <Markdown key={index} md={part.value} />;
            }
            if (part.isPayload) {
              const hasText = this.message.parts.some(
                p => p.isText && typeof p.value === 'string' && p.value.trim().length > 0
              );

              const noBorder = classNames({
                [styles.noBorder]: !hasText
              });
              return (
                <LaunchForm
                  key={index}
                  data={mockParameters(part.value)}
                  onRunSuccess={this.handleRunSuccess}
                  className={noBorder}
                />
              );
            }
            return null;
          })}
        </div>
      );
    }
    return (
      <Markdown md={this.message.text} />
    );
  };

  render () {
    return (
      <div
        style={this.props.style}
        className={classNames(
          'cp-panel', {
            [styles.messageFromUser]: this.message.fromUser,
            [styles.messageFromChat]: !this.message.fromUser,
            'table-element-selected-background-color-important': this.message.fromUser,
            [styles.messagePendingChat]: this.message.pending && !this.message.fromUser
          }
        )}
      >
        {this.pending ? (
          <TypingIndicator className={classNames(
            'cp-not-important',
            styles.typingIndicator
          )} />
        ) : (
          this.renderContent()
        )}
      </div>
    );
  }
}

Message.propTypes = {
  message: PropTypes.shape({
    text: PropTypes.string,
    error: PropTypes.string,
    id: PropTypes.number,
    fromUser: PropTypes.bool,
    pending: PropTypes.bool,
    run: PropTypes.object
  })
};
