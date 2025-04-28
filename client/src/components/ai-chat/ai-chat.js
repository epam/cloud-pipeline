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
import {Input} from 'antd';
import Message from './components/message';
import styles from './ai-chat.css';

export default class AIChat extends React.Component {
  get messages () {
    return Array.from({length: 50}, (_, i) => ({
      value: `message_${i}`,
      fromUser: i % 4 === 0
    }));
  }

  render () {
    return (
      <div className={styles.chatContainer}>
        {this.messages.map(message => (
          <Message message={message} />
        ))}
        <Input />
      </div>
    );
  }
}
