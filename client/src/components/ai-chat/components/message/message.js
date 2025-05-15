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
import {observer} from 'mobx-react';
import classNames from 'classnames';
import styles from './message.css';
import {TypingIndicator} from '../index';
import LaunchForm from '../launch-form/launch-form';
import Markdown from '../../../special/markdown';

const mockData = {
  toolId: 123,
  pipelineId: 456,
  configurationName: 'Default Configuration',
  version: '1.0.0',
  image: 'tool/image',
  registry: 'registry.example.com',
  dockerImage: 'registry.example.com/tool/image:1.0.0',
  instanceType: 't2.medium',
  disk: 100,
  is_spot: true,
  description: 'BWA is a bioinformatics software package for ' +
    'mapping low-divergent sequences against a large reference genome, such as the human genome.',
  parameters: {
    reference_path: {type: 'string', value: 'String value'},
    checkbox: {type: 'boolean', value: true},
    input: {type: 'path', value: '/path/to/file'},
    path: {type: 'path', value: '/another/path'}
  },
  optionsSelect: [
    {value: 'option1', label: 'Option 1'},
    {value: 'option2', label: 'Option 2'},
    {value: 'option3', label: 'Option 3'}
  ]
};

@observer
export default class Message extends React.Component {
  state = {
    parameters: mockData.parameters
  };
  renderContent = () => {
    const {message} = this.props;
    if (message.fromUser) {
      return <span style={{whiteSpace: 'pre-line'}}>{message.text}</span>;
    }
    return <Markdown md={message.text} />;
    // return <LaunchForm mockData={mockData} />;
  };
  render () {
    const {message} = this.props;
    return (
      <div
        style={this.props.style}
        className={classNames(
          'cp-panel', {
            [styles.messageFromUser]: message.fromUser,
            [styles.messageFromChat]: !message.fromUser,
            'table-element-selected-background-color-important': message.fromUser,
            [styles.messagePendingChat]: message.pending && !message.fromUser
          }
        )}
      >
        {message.pending ? (
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
