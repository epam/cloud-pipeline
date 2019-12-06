/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';

import styles from './NotificationView.css';
import 'highlight.js/styles/github.css';


@inject('notificationsRenderer')
@observer
export default class NotificationView extends Component {
  static propTypes = {
    text: PropTypes.string,
    style: PropTypes.shape()
  };

  render () {
    const {notificationsRenderer, style, text} = this.props;
    if (!text) {
      return null;
    }

    return (
      <div style={{height: '100%', overflowY: 'auto'}}>
        <div
          onClick={this.click}
          className={styles.mdPreview}
          style={style || {}}
          dangerouslySetInnerHTML={{__html: notificationsRenderer.render(text)}} />
      </div>
    );
  }
}
