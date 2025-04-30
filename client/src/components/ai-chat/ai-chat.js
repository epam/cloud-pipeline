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
import { observer } from 'mobx-react';
import Message from './components/message';
import AIChatEngine from './ai-chat-engine';
import EmptyChatPlaceholder from './components/empty-chat-placeholder';
import roleModel from '../../utils/roleModel';

import styles from './ai-chat.css';
import InputField from './components/input-field';
import classNames from 'classnames';

@roleModel.authenticationInfo
@observer
export default class AIChat extends React.Component {
  state = {
    userInput: ''
  };

  chat = AIChatEngine;

  componentWillUnmount() {
    if (this.chat) {
      this.chat.destroy();
    }
  }

  get currentUser() {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value
      : undefined;
  };

  onChangeUserInput = (event) => {
    this.setState({userInput: event.target.value});
  };

  onSubmitUserInput = () => {
    const {userInput} = this.state;
    if (!userInput) {
      return;
    }
    this.chat.addMessage(userInput, true);
    this.setState({userInput: ''});
  };

  render() {
    const {userInput} = this.state;
    return (
      <div className={
        classNames(
          styles.chatContainer,
          'cp-panel-no-hover',
          'cp-panel-borderless'
        )
      }>
        <div className={styles.answerArea}>
          <div className={styles.container}>
            {this.chat.messages.length
              ? this.chat.messages.map(message => (
                <Message key={message.id} message={message} />
              )) : (
                <EmptyChatPlaceholder user={this.currentUser} />
              )}
          </div>
        </div>
        <div className={styles.inputFieldArea}>
          <InputField
            value={userInput}
            onChange={this.onChangeUserInput}
            onPressEnter={this.onSubmitUserInput}
            disabled={this.chat.pending}
            onSubmit={this.onSubmitUserInput}
            onClick={this.onSubmitUserInput}
          />
        </div>
      </div>
    );
  }
}
