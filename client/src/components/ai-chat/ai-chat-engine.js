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

import {observable, computed, action} from 'mobx';
import {io} from 'socket.io-client';
import {LAUNCH_PLACEHOLDER_START, PLACEHOLDER_END} from './components/message/message-utils';

let token = 0;

const getToken = () => {
  token += 1;
  return token;
};

const base = 'https://edge.aws.cloud-pipeline.com/pipeline-75485-7860-0/';
const socketIOUrl = new URL('socket.io', base);

class ChatEngine {
  @observable _messages = [];
  @observable _pending = false;
  @observable _socket;
  @observable _chatId;

  @computed
  get messages () {
    return this._messages;
  }

  @computed
  get socket () {
    return this._socket;
  }

  @computed
  get pending () {
    return this._pending;
  }

  @action
  ask = (text) => {
    const message = {
      text,
      id: getToken(),
      fromUser: true,
      pending: false
    };
    this._messages.push(message);
    this.sendUserMessage(message);
  };

  @action
  sendSystemMessage = ({text = '', run}) => {
    const systemMessage = {
      run,
      text,
      id: getToken(),
      fromUser: false,
      pending: false,
      error: ''
    };
    this._messages.push(systemMessage);
  };

  postMessage = async (message) => {
    const fetchOptions = {
      mode: 'cors',
      credentials: 'include',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=UTF-8;'
      },
      body: JSON.stringify({
        role: 'user',
        content: message.text
      })
    };
    const response = await fetch(
      `${base}chat/${this._chatId}/message`,
      fetchOptions
    );
    if (response.error) {
      this.error = response.error;
    }
  };

  @action
  sendUserMessage = async (message) => {
    this._pending = true;
    const responseMessage = observable({
      text: '',
      run: undefined,
      id: getToken(),
      fromUser: false,
      pending: true,
      error: ''
    });
    this._messages.push(responseMessage);
    try {
      await this.initializeChat(responseMessage);
      await this.postMessage(message);
      this._socket.emit('assistant', {
        'chat_id': this._chatId
      });
    } catch (e) {
      this._pending = false;
    }
  };

  @action
  onDone = () => {
    this._pending = false;
    if (this._socket) {
      this._socket.close();
    }
  };

  @action
  onChunk = (responseMessage) => {
    let prevChunk = '';
    let temp = '';
    let streamingPaused = false;
    const hasPlaceholders = (text) => {
      return text.includes(LAUNCH_PLACEHOLDER_START);
    };
    return (chunk = '') => {
      if (responseMessage.pending) {
        responseMessage.pending = false;
      }
      if (streamingPaused || hasPlaceholders(prevChunk.concat(chunk))) {
        streamingPaused = true;
        temp += chunk;
        if (chunk.includes(PLACEHOLDER_END) || temp.includes(PLACEHOLDER_END)) {
          streamingPaused = false;
          responseMessage.text += temp;
          temp = '';
          return;
        }
      } else {
        responseMessage.text += chunk;
      }
      prevChunk = chunk;
    };
  };

  @action
  onError = (responseMessage) => (error = '') => {
    if (responseMessage.pending) {
      responseMessage.pending = false;
    }
    responseMessage.error = error;
  };

  @action
  destroy = () => {
    this._pending = false;
    if (this._socket) {
      this._socket.close();
      this._socket = null;
    }
  }

  @action
  clear = () => {
    this._pending = false;
    this._messages = [];
    this._chatId = undefined;
    if (this._socket) {
      this._socket.close();
      this._socket = null;
    }
  };

  registerNewChat = async () => {
    const fetchOptions = {
      mode: 'cors',
      credentials: 'include',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=UTF-8;'
      },
      body: JSON.stringify({})
    };
    const response = await fetch(`${base}chat`, fetchOptions);
    if (response.error) {
      this.error = response.error;
      return;
    }
    const id = await response.json();
    this._chatId = id;
  };

  initializeChat = async (responseMessage) => {
    if (!this._chatId) {
      await this.registerNewChat();
    }
    const waitUntilConnect = () => new Promise((resolve, reject) => {
      const errorGenerator = (socketError) => () => {
        reject(new Error(socketError));
      };
      this._socket.on('connect', resolve);
      const onConnectError = errorGenerator('socket connect error');
      const onDisconnect = errorGenerator('socket disconnected');
      this._socket.on('connect_error', onConnectError);
      this._socket.on('disconnect', onDisconnect);
    });
    try {
      if (this._socket) {
        this._socket.close();
      }
      this._socket = io(socketIOUrl.origin, {
        withCredentials: true,
        secure: true,
        path: socketIOUrl.pathname,
        transports: ['websocket']
      });
      await waitUntilConnect();
      this._socket.on('done', this.onDone);
      this._socket.on('error', this.onError(responseMessage));
      this._socket.on('chunk', this.onChunk(responseMessage));
      this._socket.on('disconnect', this.onDone);
    } catch (e) {
      responseMessage.pending = false;
      this._pending = false;
      responseMessage.error = e.message;
      console.error('Error while creating chat:', e);
      this.onDone();
    }
  };
}

const AIChatEngine = new ChatEngine();

export default AIChatEngine;
