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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Icon, Row} from 'antd';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import styles from './preview.css';
import MetadataEntityLoad from '../../../models/folderMetadata/MetadataEntityLoad';

@inject((stores, params) => {
  const {item} = params;
  const metadataEntity = new MetadataEntityLoad(item.id);

  metadataEntity.fetch();

  return {
    metadataEntity
  };
})
@observer
export default class MetadataEntityPreview extends React.Component {

  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    })
  };

  @computed
  get description () {
    if (!this.props.item) {
      return null;
    }
    if (this.props.metadataEntity && this.props.metadataEntity.loaded &&
      this.props.metadataEntity.value.classEntity) {
      return this.props.metadataEntity.value.classEntity.name;
    }
    return this.props.item.description;
  }

  @computed
  get rowData () {
    if (this.props.metadataEntity.loaded) {
      return this.props.metadataEntity.value.data || null;
    }

    return null;
  }

  renderItems = () => {
    if (!this.props.metadataEntity) {
      return null;
    }
    if (this.props.metadataEntity.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.metadataEntity.error) {
      return (
        <div className={styles.contentPreview}>
          <span style={{color: '#ff556b'}}>{this.props.metadataEntity.error}</span>
        </div>
      );
    }
    if (!this.rowData) {
      return null;
    }

    const padding = 20;
    const firstCellStyle = {
      paddingRight: padding
    };

    const items = [];
    for (let key in this.rowData) {
      if (this.rowData.hasOwnProperty(key)) {
        items.push(
          <tr key={key}>
            <td style={firstCellStyle}>{key}</td>
            <td>{this.rowData[key].value}</td>
          </tr>
        );
      }
    }

    return (
      <div className={styles.contentPreview}>
        <table>
          <tbody>
            {items}
          </tbody>
        </table>
      </div>
    );
  };

  render () {
    if (!this.props.item) {
      return null;
    }

    const highlights = renderHighlights(this.props.item);
    const items = this.renderItems();

    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.props.item.name}</span>
          </Row>
          {
            this.description &&
            <Row className={styles.description}>
              {this.description}
            </Row>
          }
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {items && renderSeparator()}
          {items}
        </div>
      </div>
    );
  }

}
