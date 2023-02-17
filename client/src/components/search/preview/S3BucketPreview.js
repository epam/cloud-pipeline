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
import {inject, observer} from 'mobx-react';
import AWSRegionTag from '../../special/AWSRegionTag';
import {Icon, Row} from 'antd';
import classNames from 'classnames';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import styles from './preview.css';
import DataStorageRequest from '../../../models/dataStorage/DataStoragePage';
import displayDate from '../../../utils/displayDate';
import displaySize from '../../../utils/displaySize';

const PAGE_SIZE = 100;

@inject(({dataStorages}, params) => {
  return {
    dataStorageInfo: params.item && params.item.id
      ? dataStorages.load(params.item.id)
      : null,
    items: params.item && params.item.id
      ? new DataStorageRequest(
        params.item.id,
        null,
        false,
        false,
        PAGE_SIZE
      ) : null
  };
})
@observer
export default class S3BucketPreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    })
  };

  renderItems = () => {
    if (!this.props.items) {
      return null;
    }
    if (this.props.items.pending) {
      return <Row className={styles.contentPreview} type="flex" justify="center"><Icon type="loading" /></Row>;
    }
    if (this.props.items.error) {
      return (
        <div className={styles.contentPreview}>
          <span className={'cp-search-preview-error'}>{this.props.items.error}</span>
        </div>
      );
    }
    const items = (this.props.items.value.results || []).map(i => i);
    return (
      <div className={styles.contentPreview}>
        <table>
          <tbody>
            {
              items.map((item, index) => {
                return (
                  <tr key={index}>
                    <td className={styles.firstCell}>
                      {
                        item.type.toLowerCase() === 'folder'
                          ? <Icon type="folder" />
                          : <Icon type="file" />
                      }
                      <span style={{paddingLeft: 5}}>{item.name}</span>
                    </td>
                    <td className={styles.intermediaCell}>
                      {displaySize(item.size)}
                    </td>
                    <td className={styles.lastCell}>{displayDate(item.changed)}</td>
                  </tr>
                );
              })
            }
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
    const propsDescription = this.props.item.description;
    const loadedDescription = this.props.dataStorageInfo && this.props.dataStorageInfo.loaded
      ? this.props.dataStorageInfo.value.description
      : null;
    const description = loadedDescription || propsDescription;
    const items = this.renderItems();
    return (
      <div
        className={
          classNames(
            styles.container,
            'cp-search-container'
          )
        }
      >
        <div className={styles.header}>
          <Row className={classNames(styles.title, 'cp-search-header-title')} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span style={{padding: '0px 5px'}}>{this.props.item.name}</span>
            {
              this.props.dataStorageInfo && this.props.dataStorageInfo.loaded &&
              <AWSRegionTag
                regionId={this.props.dataStorageInfo.value.regionId}
                displayName />
            }
            {
              this.props.dataStorageInfo &&
              this.props.dataStorageInfo.loaded &&
              this.props.dataStorageInfo.value.sensitive &&
              (
                <span
                  className="cp-sensitive-tag"
                  style={{
                    fontWeight: 'bold',
                    padding: '2px 5px',
                    borderRadius: 5,
                    lineHeight: 1,
                    fontSize: 'smaller'
                  }}
                >
                  sensitive
                </span>
              )
            }
          </Row>
          {
            description &&
            <Row className={classNames(styles.description, 'cp-search-header-description')}>
              {description}
            </Row>
          }
        </div>
        <div className={classNames(styles.content, 'cp-search-content')}>
          {highlights && renderSeparator()}
          {highlights}
          {items && renderSeparator()}
          {items}
        </div>
      </div>
    );
  }
}
