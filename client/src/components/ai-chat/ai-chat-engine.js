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

let token = 0;

const getToken = () => {
  token += 1;
  return token;
};

export const LAUNCH_PLACEHOLDER_START = '<<<LAUNCH:';
export const PLACEHOLDER_END = '>>>';

const base = 'https://edge.aws.cloud-pipeline.com/pipeline-74205-7860-0/';
const socketIOUrl = new URL('socket.io', base);

class ChatEngine {
  @observable _messages = [];
  @observable _pending = false;
  @observable _socket;
  @observable _error = '';

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

  @computed
  get error () {
    return this._error;
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
    this.sendUserMessage();
  };

  @action
  sendUserMessage = async () => {
    this._pending = true;
    const responseMessage = observable({
      text: '',
      id: getToken(),
      fromUser: false,
      pending: true,
      error: ''
    });
    this._messages.push(responseMessage);
    try {
      await this.createChat(responseMessage);
      this._socket.emit('assistant', {
        messages: this.messages
          .filter(message => message !== responseMessage)
          .map(message => ({
            content: message.text,
            role: message.fromUser ? 'user' : 'assistant'
          }))
      });
    } catch (e) {
      this._pending = false;
      this._error = e.message;
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
    this._messages = [];
    this._pending = false;
    token = 0;
    if (this._socket) {
      this._socket.close();
      this._socket = null;
    }
  }

  createChat = async (responseMessage) => {
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
        // secure: true,
        path: socketIOUrl.pathname,
        transports: ['websocket']
      });
      await waitUntilConnect();
      this._socket.on('done', this.onDone);
      this._socket.on('error', this.onError(responseMessage));
      this._socket.on('chunk', this.onChunk(responseMessage));
      this._socket.on('disconnect', this.onDone);
    } catch (e) {
      this._error = e.message;
      this._socket && this._socket.close();
      console.error('Error creating chat:', e);
    }
  }
}

const AIChatEngine = new ChatEngine();

export default AIChatEngine;
