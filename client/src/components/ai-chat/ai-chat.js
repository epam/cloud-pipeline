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
import {Alert} from 'antd';
import {DownCircleOutlined} from '@ant-design/icons';
import {observable, computed, makeObservable} from 'mobx';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import AIChatEngine from './ai-chat-engine';
import roleModel from '../../utils/roleModel';
import styles from './ai-chat.css';
import {Message, InputField, EmptyChatPlaceholder} from './components';

let _chatEngine;
/**
 * @returns {AiChatEngine}
 */
const getSharedChatEngine = () => {
  if (!_chatEngine) {
    _chatEngine = new AIChatEngine();
  }
  return _chatEngine;
};

@roleModel.authenticationInfo
@observer
export default class AIChat extends React.Component {
  state = {
    userInput: ''
  };

  chatEngine = getSharedChatEngine();
  answersContainerRef;
  scrollContainerRef;
  scrollPositionRAF;
  isScrollingTimeout;
  _scrolledDown = true;
  _isScrolling = false;

  constructor (props) {
    super(props);
    makeObservable(this, {
      chatEngine: observable,
      _scrolledDown: observable,
      _isScrolling: observable,
      isScrolling: computed,
      scrolledDown: computed
    });
  }

  componentDidMount () {
    (this.chatEngine.reloadMessages)();
    this.arrangeDownButton();
  }

  componentWillUnmount () {
    this.chatEngine.stopListeningMessage();
    if (this.scrollPositionRAF) {
      cancelAnimationFrame(this.scrollPositionRAF);
    }
  }

  get isScrolling () {
    return this._isScrolling;
  }

  get scrolledDown () {
    return this._scrolledDown;
  }

  get currentUser () {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value
      : undefined;
  }

  get scrollContainerHeight () {
    if (!this.scrollContainerRef) {
      return 0;
    }
    return this.scrollContainerRef.clientHeight;
  }

  onChangeUserInput = (event) => {
    if (this.chatEngine.pending) {
      return;
    }
    this.setState({userInput: event.target.value});
  };

  onSubmitUserInput = async (event) => {
    const {userInput} = this.state;
    if (this.chatEngine.pending || event.shiftKey || !userInput) {
      return;
    }
    try {
      await this.chatEngine.sendMessage({text: userInput});
      this.setState({userInput: ''});
      setTimeout(this.scrollToBottom, 100);
    } catch {
      // noop
    }
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

  onRunLaunchSuccess = async (run) => {
    if (this.chatEngine) {
      await this.chatEngine.sendMessage({data: {run_payload: {id: run.id}}, type: 'run'});
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
    const chat = this.chatEngine;
    return (
      <div
        className={
          classNames(
            styles.chatContainer,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
      >
        <div className={styles.overflowContainer} ref={el => {
          this.scrollContainerRef = el;
        }}>
          <div
            ref={el => {
              this.answersContainerRef = el;
            }}
            className={classNames(styles.answerArea, styles.chatArea)}
          >
            {chat.messages.length
              ? chat.messages.map((message, index) => (
                <div
                  key={message.identifier}
                  style={{
                    minHeight: index === chat.messages.length - 1
                      ? `calc(${this.scrollContainerHeight}px - 100px)`
                      : 'auto',
                    marginBottom: index === chat.messages.length - 1
                      ? '40px'
                      : 0
                  }}
                  data-id={message.id}
                >
                  <Message
                    message={message}
                    onRunLaunchSuccess={this.onRunLaunchSuccess}
                    first={index === 0}
                    last={index === chat.messages.length - 1}
                  />
                </div>
              )) : (
                <EmptyChatPlaceholder user={this.currentUser} />
              )}
          </div>
        </div>
        {
          chat.error && (
            <div className={classNames(styles.chatError, styles.chatArea)}>
              <Alert
                message={(
                  <div>
                    <span>{chat.error}</span>
                    <a style={{marginLeft: 5}} onClick={() => chat.reload()}>
                      Reload
                    </a>
                  </div>
                )}
                type="error"
                showIcon
                style={{width: '100%'}}
              />
            </div>
          )
        }
        {
          chat.socketError && (
            <div className={classNames(styles.chatError, styles.chatArea)}>
              <Alert message={chat.socketError} type="error" showIcon style={{width: '100%'}} />
            </div>
          )
        }
        {
          chat.messageError && (
            <div className={classNames(styles.chatError, styles.chatArea)}>
              <Alert message={chat.messageError} type="error" showIcon style={{width: '100%'}} />
            </div>
          )
        }
        <div className={classNames(
          styles.inputFieldArea,
          styles.chatArea,
          'cp-panel-color', {
            [styles.dropShadow]: !this.scrolledDown
          })}>
          <DownCircleOutlined className={classNames('cp-panel-background-color cp-primary', styles.downButton, {[styles.visible]: !this.isScrolling && !this.scrolledDown})} onClick={this.scrollToBottom} />
          {chat.messages?.length ? (
            <a className={styles.clearButton} onClick={() => chat.changeChat()}>
              New chat
            </a>
          ) : null}
          <InputField
            value={userInput}
            disabled={chat.pending || !chat.initialized}
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
