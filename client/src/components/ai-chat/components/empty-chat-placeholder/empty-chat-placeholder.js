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
import styles from './empty-chat-placeholder.css';
import classNames from 'classnames';
import UserName from '../../../special/UserName';

export default function EmptyChatPlaceholder ({user}) {
  return (
    <div className={styles.chatPlaceholder}>
      <span className={classNames(styles.chatPlaceholderUserName, 'cp-text')}>
        <span>Hello</span>
        {
          user && (
            <span>
              <span style={{whiteSpace: 'pre'}}>{' '}</span>
              <UserName userName={user.userName} />
              <span style={{whiteSpace: 'pre'}}>{','}</span>
            </span>
          )
        }
      </span>
      <span className={classNames(styles.chatPlaceholderText, 'cp-text')}>
        how can i help you?
      </span>
    </div>
  );
}

EmptyChatPlaceholder.propTypes = {
  user: PropTypes.object
};
