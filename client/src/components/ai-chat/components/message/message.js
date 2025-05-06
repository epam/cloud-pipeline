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
import Markdown from '../../../special/markdown';
import TypingIndicator from '../typing-indicator/typing-indicator';
import styles from './message.css';

@observer
export default class Message extends React.Component {
  renderContent = () => {
    const {message} = this.props;
    if (message.fromUser) {
      return <span style={{whiteSpace: 'pre-line'}}>{message.text}</span>;
    }
    return <Markdown md={message.text} />;
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
