/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon, Row} from 'antd';
import classNames from 'classnames';
import renderSeparator from './renderSeparator';
import {metadataLoad, renderAttributes} from './renderAttributes';
import {PreviewIcons} from './previewIcons';
import ItemTypes from './itemTypes';
import styles from './preview.css';
import VSIPreview from './vsi-preview';

const previewLoad = (params, dataStorageCache) => {
  if (params.item && params.storageId && params.item.path) {
    return dataStorageCache.getContent(
      params.storageId,
      params.item.path
    );
  } else {
    return null;
  }
};

const downloadUrlLoad = (params, dataStorageCache) => {
  if (params.item && params.storageId && params.item.path) {
    return dataStorageCache.getDownloadUrl(
      params.storageId,
      params.item.path
    );
  } else {
    return null;
  }
};

@inject('metadataCache', 'dataStorageCache')
@inject((stores, params) => {
  const {dataStorageCache, dataStorages} = stores;
  return {
    preview: previewLoad(params, dataStorageCache),
    downloadUrl: downloadUrlLoad(params, dataStorageCache),
    dataStorageInfo: params.item && params.storageId
      ? dataStorages.load(params.storageId)
      : null,
    metadata: metadataLoad(params, 'DATA_STORAGE_ITEM', stores)
  };
})
@observer
export default class S3FilePreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    }),
    lightMode: PropTypes.bool,
    onPreviewLoaded: PropTypes.func,
    fullscreen: PropTypes.bool,
    onFullScreenChange: PropTypes.func,
    fullScreenAvailable: PropTypes.bool,
    storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
  };

  state = {
    pdbError: null,
    imageError: null
  };

  @computed
  get filePreview () {
    if (this.props.preview) {
      if (this.props.preview.pending) {
        return null;
      }
      const preview = this.props.preview.value.content
        ? atob(this.props.preview.value.content)
        : '';
      const truncated = this.props.preview.value.truncated;
      const noContent = !preview;
      const mayBeBinary = this.props.preview.value.mayBeBinary;
      const error = this.props.preview.error;
      const extension = this.props.preview.path
        ? this.props.preview.path.split('.').pop().toLowerCase()
        : undefined;
      return {
        preview,
        truncated,
        noContent,
        error,
        mayBeBinary,
        extension
      };
    }
    return null;
  }

  renderInfo = () => {
    if (!this.props.dataStorageInfo) {
      return null;
    }
    if (this.props.dataStorageInfo.pending) {
      return (
        <Row className={styles.info}>
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.dataStorageInfo.error) {
      return <span style={{color: '#ff556b'}}>{this.props.dataStorageInfo.error}</span>;
    }
    const path = this.props.item.type !== ItemTypes.NFSFile
      ? [this.props.dataStorageInfo.value.pathMask, ...this.props.item.path.split('/')]
      : this.props.item.path.split('/').filter(p => !!p);
    return (
      <div className={styles.info}>
        <table>
          <tbody>
            <tr>
              <td style={{whiteSpace: 'nowrap', verticalAlign: 'top'}}>Storage:</td>
              <td style={{paddingLeft: 5}}>
                {this.props.dataStorageInfo.value.name}
              </td>
            </tr>
            <tr>
              <td style={{whiteSpace: 'nowrap', verticalAlign: 'top'}}>Full path:</td>
              <td style={{paddingLeft: 5}}>
                {
                  path.reduce((result, current, index, arr) => {
                    result.push(<code key={index}>{current}</code>);
                    if (index < arr.length - 1) {
                      result.push(<Icon key={`sep_${index}`} type="caret-right" />);
                    }
                    return result;
                  }, [])
                }
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  };

  renderVSIPreview = () => {
    return (
      <VSIPreview
        className={styles.contentPreview}
        file={this.props.item.path}
        storageId={this.props.storageId}
        onPreviewLoaded={this.props.onPreviewLoaded}
        fullscreen={this.props.fullscreen}
        onFullScreenChange={this.props.onFullScreenChange}
        fullScreenAvailable={this.props.fullScreenAvailable}
      />
    );
  };

  renderPreview = () => {
    if (this.props.dataStorageInfo && !this.props.dataStorageInfo.loaded) {
      return;
    }
    if (
      this.props.dataStorageInfo &&
      this.props.dataStorageInfo.value &&
      this.props.dataStorageInfo.value.sensitive
    ) {
      return null;
    }
    const extension = this.props.item.path.split('.').pop().toLowerCase();
    const previewRenderers = {
      vsi: this.renderVSIPreview,
      mrxs: this.renderVSIPreview
    };
    if (previewRenderers[extension]) {
      const preview = previewRenderers[extension]();
      if (preview) {
        return preview;
      }
    }
    return this.renderTextFilePreview();
  };

  render () {
    if (!this.props.item) {
      return null;
    }
    const info = this.renderInfo();
    const attributes = renderAttributes(this.props.metadata, true);
    const preview = this.renderPreview();
    return (
      <div
        className={
          classNames(
            styles.container,
            {
              [styles.light]: this.props.lightMode
            }
          )
        }
      >
        <div className={styles.header}>
          <Row className={styles.title}>
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
          {info && renderSeparator()}
          {info}
          {attributes && renderSeparator()}
          {attributes}
          {preview && renderSeparator()}
          {preview}
        </div>
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.item !== this.props.item) {
      this.setState({pdbError: null, imageError: null});
    }
  }
}
