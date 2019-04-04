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
import {computed} from 'mobx';
import AWSRegionTag from '../../special/AWSRegionTag';
import {Icon, Row} from 'antd';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {renderAttributes} from './renderAttributes';
import {PreviewIcons} from './previewIcons';
import {SearchItemTypes} from '../../../models/search';
import styles from './preview.css';

const MAX_ITEMS = 40;
const SHOW_DESCRIPTIONS = true;

@inject(({folders}, params) => {
  return {
    folder: folders.load(params.item.id)
  };
})
@observer
export default class FolderPreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string
    })
  };

  @computed
  get folderMetadataTags () {
    const matadataValue = {};
    for (let tag in this.props.folder.value.objectMetadata || {}) {
      if (this.props.folder.value.objectMetadata.hasOwnProperty(tag)) {
        matadataValue[tag] = this.props.folder.value.objectMetadata[tag].value;
      }
    }
    return {
      pending: this.props.folder.pending,
      error: this.props.folder.error,
      value: matadataValue || {}
    };
  }

  renderMetadata = (metadata) => {
    if (!metadata) {
      return null;
    }
    const items = [];
    for (let key in metadata) {
      if (metadata.hasOwnProperty(key)) {
        items.push(
          <div key={key} className={styles.attribute}>
            <div className={styles.attributeName}>{key}</div>
            <div className={styles.attributeValue}>{metadata[key].value}</div>
          </div>
        );
      }
    }
    return <Row type="flex">{items}</Row>;
  };

  renderItems = () => {
    if (!this.props.folder) {
      return null;
    }
    if (this.props.folder.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.folder.error) {
      return (
        <div className={styles.contentPreview}>
          <span style={{color: '#ff556b'}}>{this.props.folder.error}</span>
        </div>
      );
    }
    const renderName = (item) => {
      let nameComponent;
      let nameStyle;
      if (SHOW_DESCRIPTIONS) {
        nameStyle = {
          fontWeight: 'bold'
        };
      }

      if (item.type === SearchItemTypes.s3Bucket || item.type === SearchItemTypes.gsStorage) {
        nameComponent = (
          <span>
            <span style={nameStyle}>{item.name}</span><AWSRegionTag regionId={item.regionId} />
          </span>
        );
      } else {
        nameComponent = <span style={nameStyle}>{item.name}</span>;
      }
      if (SHOW_DESCRIPTIONS && (item.description || item.hasMetadata)) {
        nameComponent = (
          <Row>
            <Row style={{marginTop: 2}}>{nameComponent}</Row>
            <Row
              style={{
                marginTop: 5
              }}>
              {
                item.description &&
                <Row
                  style={
                    item.hasMetadata
                      ? {marginBottom: 5, wordWrap: 'normal'}
                      : {wordWrap: 'normal'}
                  }
                  className="object-description">{item.description}</Row>
              }
              {
                this.renderMetadata(item.objectMetadata)
              }
            </Row>
          </Row>
        );
      }

      return nameComponent;
    };
    const mapStorageType = item => {
      let type;
      switch ((item.type || '').toLowerCase()) {
        case 's3': type = SearchItemTypes.s3Bucket; break;
        case 'nfs': type = SearchItemTypes.NFSBucket; break;
        case 'gs': type = SearchItemTypes.gsStorage; break;
        default: type = item.aclClass; break;
      }
      return type;
    };
    const mapChild = (item) => ({
      type: item.aclClass.toLowerCase() === 'data_storage'
        ? mapStorageType(item)
        : item.aclClass,
      name: item.name,
      description: item.description || undefined,
      hasMetadata: item.hasMetadata || undefined,
      objectMetadata: item.objectMetadata || undefined,
      regionId: item.regionId || undefined
    });
    const items = [
      ...(this.props.folder.value.childFolders || []).map(mapChild),
      ...(this.props.folder.value.storages || []).map(mapChild),
      ...(this.props.folder.value.pipelines || []).map(mapChild),
      ...(this.props.folder.value.configurations || []).map(mapChild),
      ...(this.props.folder.value.metadata ? [{name: 'Metadata', icon: 'appstore-o'}] : [])
    ];
    const padding = 20;
    const firstCellStyle = {
      paddingRight: padding
    };
    const rowStyle = {
      // borderBottom: '1px solid #555'
    };
    return (
      <div className={styles.contentPreview}>
        <table>
          <tbody>
            {
              (items.length > MAX_ITEMS
                ? items.slice(0, MAX_ITEMS)
                : items || []).map((item, index) => {
                  return (
                    <tr key={index} style={rowStyle}>
                      <td style={firstCellStyle}>
                        {
                          PreviewIcons[item.type]
                            ? <Icon
                              className={styles.searchResultItemIcon}
                              type={PreviewIcons[item.type]} />
                            : item.icon && <Icon
                              className={styles.searchResultItemIcon}
                              type={item.icon} />
                        }
                      </td>
                      <td>
                        {renderName(item)}
                      </td>
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
    const attributes = renderAttributes(this.folderMetadataTags, true);
    const items = this.renderItems();

    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.props.item.name}</span>
          </Row>
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {attributes && renderSeparator()}
          {attributes}
          {items && renderSeparator()}
          {items}
        </div>
      </div>
    );
  }
}
