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
import {mockStream} from './mocks';

let token = 0;

const getToken = () => {
  token += 1;
  return token;
};

class ChatEngine {
  @observable _messages = [];
  @observable _pending = false;

  @computed
  get messages () {
    return this._messages;
  }

  @computed
  get pending () {
    return this._pending;
  }

  @action
  addMessage = (text, fromUser = false) => {
    const message = {
      text,
      id: getToken(),
      fromUser,
      pending: false
    };
    this._messages.push(message);
    if (fromUser) {
      this.sendUserMessage(message);
    }
  };

  @action
  sendUserMessage = async (message) => {
    this._pending = true;
    const id = getToken();
    const responseMessage = observable({
      text: '',
      id,
      fromUser: false,
      pending: true
    });
    this._messages.push(responseMessage);
    await mockStream(message, (chunk, finished) => {
      responseMessage.text += ` ${chunk}`;
      if (finished) {
        responseMessage.pending = false;
      }
    });
    this._pending = false;
  };

  @action
  destroy = () => {
    this._messages = [];
    this._pending = false;
    token = 0;
  }
}

const AIChatEngine = new ChatEngine();

export default AIChatEngine;
