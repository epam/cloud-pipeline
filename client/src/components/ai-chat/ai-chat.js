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
import {Icon} from 'antd';
import {observable, computed} from 'mobx';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import AIChatEngine from './ai-chat-engine';
import roleModel from '../../utils/roleModel';
import styles from './ai-chat.css';
import {Message, InputField, EmptyChatPlaceholder} from './components';

@roleModel.authenticationInfo
@observer
export default class AIChat extends React.Component {
  state = {
    userInput: ''
  };

  chat = AIChatEngine;
  answersContainerRef;
  scrollContainerRef;
  scrollPositionRAF;
  isScrollingTimeout;

  @observable _scrolledDown = true;
  @observable _isScrolling = false;

  componentDidMount () {
    this.arrangeDownButton();
  }

  componentWillUnmount () {
    if (this.chat) {
      this.chat.destroy();
    }
    if (this.scrollPositionRAF) {
      cancelAnimationFrame(this.scrollPositionRAF);
    }
  }

  @computed
  get isScrolling () {
    return this._isScrolling;
  }

  @computed
  get scrolledDown () {
    return this._scrolledDown;
  }

  get currentUser () {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value
      : undefined;
  };

  get scrollContainerHeight () {
    if (!this.scrollContainerRef) {
      return 0;
    }
    return this.scrollContainerRef.clientHeight;
  }

  onChangeUserInput = (event) => {
    if (this.chat.pending) {
      return;
    }
    this.setState({userInput: event.target.value});
  };

  onSubmitUserInput = (event) => {
    const {userInput} = this.state;
    if (this.chat.pending || event.shiftKey || !userInput) {
      return;
    }
    this.chat.ask(userInput);
    this.setState({userInput: ''}, () => {
      const lastUserMessage = this.chat.messages
        .findLast(message => message.fromUser);
      this.scrollToMessage(lastUserMessage.id);
    });
  };

  scrollToMessage = (id) => {
    const index = [...this.answersContainerRef.children]
      .findIndex(node => node?.dataset?.id === `${id}`);
    if (index >= 0 && this.answersContainerRef.children[index]) {
      this.blockChecksWhileScrolling();
      this.answersContainerRef.children[index].scrollIntoView({
        behavior: 'smooth'
      });
    }
  };

  blockChecksWhileScrolling = () => {
    if (this.isScrollingTimeout) {
      clearTimeout(this.isScrollingTimeout);
    }
    this._isScrolling = true;
    this.isScrollingTimeout = setTimeout(() => {
      this._isScrolling = false;
    }, 1500);
  };

  onRunLaunchSuccess = (run) => {
    if (this.chat) {
      this.chat.sendSystemMessage({text: '', run});
      this.scrollToBottom();
    }
  };

  scrollToBottom = (smooth = true) => {
    if (!this.scrollContainerRef) {
      return;
    }
    this.blockChecksWhileScrolling();
    this.scrollContainerRef.scrollTo({
      top: this.scrollContainerRef.scrollHeight,
      behavior: smooth ? 'smooth' : 'auto'
    });
  };

  arrangeDownButton = () => {
    this.scrollPositionRAF = requestAnimationFrame(this.arrangeDownButton);
    if (!this.scrollContainerRef) {
      return;
    }
    const {scrollTop, clientHeight, scrollHeight} = this.scrollContainerRef;
    const scrolledDown = scrollTop + clientHeight >= scrollHeight - 20;
    if (!this.isScrolling && scrolledDown !== this.scrolledDown) {
      this._scrolledDown = scrolledDown;
    }
  };

  render () {
    const {userInput} = this.state;
    return (
      <div className={
        classNames(
          styles.chatContainer,
          'cp-panel',
          'cp-panel-no-hover',
          'cp-panel-borderless'
        )
      }>
        <div className={styles.overflowContainer} ref={el => {
          this.scrollContainerRef = el;
        }}>
          <div
            ref={el => {
              this.answersContainerRef = el;
            }}
            className={styles.answerArea}
          >
            {this.chat.messages.length
              ? this.chat.messages.map((message, index) => (
                <div
                  key={message.id}
                  style={{
                    minHeight: index === this.chat.messages.length - 1
                      ? `calc(${this.scrollContainerHeight}px - 100px)`
                      : 'auto',
                    marginBottom: index === this.chat.messages.length - 1
                      ? '40px'
                      : 0
                  }}
                  data-id={message.id}
                >
                  <Message
                    message={message}
                    onRunLaunchSuccess={this.onRunLaunchSuccess}
                  />
                </div>
              )) : (
                <EmptyChatPlaceholder user={this.currentUser} />
              )}
          </div>
        </div>
        <div className={classNames(
          styles.inputFieldArea,
          'cp-panel-color', {
            [styles.dropShadow]: !this.scrolledDown
          })}>
          <Icon
            type="down-circle-o"
            className={classNames(
              'cp-panel-background-color cp-primary',
              styles.downButton, {
                [styles.visible]: !this.isScrolling &&
                !this.scrolledDown
              }
            )}
            onClick={this.scrollToBottom}
          />
          {this.chat.messages?.length ? (
            <a className={styles.clearButton} onClick={this.chat.clear}>
              Clear chat
            </a>
          ) : null}
          <InputField
            value={userInput}
            onChange={this.onChangeUserInput}
            onPressEnter={this.onSubmitUserInput}
            onSubmit={this.onSubmitUserInput}
            onClick={this.onSubmitUserInput}
            onKeyDown={(event) => {
              if (!event.shiftKey && event.key.toLowerCase() === 'enter') {
                event.preventDefault();
                return false;
              }
            }}
          />
        </div>
      </div>
    );
  }
}
