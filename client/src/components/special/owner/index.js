/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon} from 'antd';
import UserName from '../UserName';
import styles from './owner.css';

function Owner ({subject, style}) {
  if (subject && subject.owner) {
    return (
      <div className={styles.container} style={style}>
        <Icon className={styles.user} type="user" />
        <UserName userName={subject.owner} />
      </div>
    );
  }
  return null;
}

Owner.propTypes = {
  subject: PropTypes.object,
  style: PropTypes.object
};

export default Owner;
