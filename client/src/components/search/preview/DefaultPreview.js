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

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Icon, Row} from 'antd';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import styles from './preview.css';
@observer
export default class DefaultPreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string,
    })
  };

  render () {
    if (!this.props.item) {
      return null;
    }
    const highlights = renderHighlights(this.props.item);
    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.props.item.name}</span>
          </Row>
          {
            this.props.item.description &&
            <Row className={styles.description}>
              {this.props.item.description}
            </Row>
          }
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
        </div>
      </div>
    );
  }
}
