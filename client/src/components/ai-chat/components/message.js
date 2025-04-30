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
import {observer} from 'mobx-react';
import Markdown from '../../special/markdown';
import styles from './message.css';

@observer
export default class Message extends React.Component {
  render () {
    const {message} = this.props;
    return (
      <div
        className={message.fromUser ? styles.messageFromUser : styles.messageFromChat}
      >
        <div className={styles.answer}>
          <Markdown md={message.text} />
        </div>
      </div>
    );
  }
}
