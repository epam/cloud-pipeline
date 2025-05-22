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
import {TypingIndicator} from '../index';
import LaunchForm from '../launch-form/launch-form';
import Markdown from '../../../special/markdown';
import {processMessage} from './message-utils';
import styles from './message.css';

const mockParameters = {
  reference_path: {type: 'string', value: 'String value'},
  checkbox: {type: 'boolean', value: true, checkboxText: 'Enable feature'},
  input: {type: 'path', value: '/path/to/file'},
  path: {type: 'path', value: '/another/path'},
};

const processPartValue = (value) => {
  if (!value.parameters || Object.keys(value.parameters).length === 0) {
    return {
      ...value,
      configurationName: 'Default Configuration',
      version: '1.0.0',
      description: 'BWA is a bioinformatics software package for ' +
      'mapping low-divergent sequences against a large reference genome, such as the human genome.',
      parameters: mockParameters
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

  renderContent = () => {
    if (this.message.fromUser) {
      return <span style={{whiteSpace: 'pre-line'}}>{this.message.text}</span>;
    }

    if (this.message.parts.length > 0) {
      return (
        <div>
          {this.message.parts.map((part, index) => {
            if (part.isText && part.value) {
              return <Markdown key={index} md={part.value} />;
            }
            if (part.isPayload) {
              console.log(part.value);

              return (
                <LaunchForm
                  key={index}
                  data={processPartValue(part.value)}
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
    id: PropTypes.number,
    fromUser: PropTypes.bool,
    pending: PropTypes.bool
  })
};
