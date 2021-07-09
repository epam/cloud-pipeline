/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {
  Tabs
} from 'antd';
import DataStorageRequest from '../../../../models/dataStorage/DataStoragePage';
import styles from '../preview.css';

@inject('dataStorageCache')
@observer
class VSIPreview extends React.Component {
  state = {
    items: [],
    preview: undefined,
    active: undefined,
    pending: false
  };

  componentDidMount () {
    this.fetchPreviewItems();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.storageId !== this.props.storageId || prevProps.file !== this.props.file) {
      this.fetchPreviewItems();
    }
  }

  onChangePreview = (path) => {
    if (!path) {
      this.setState({
        active: path,
        preview: undefined
      });
      return;
    }
    this.setState({
      active: path,
      preview: {
        pending: true
      }
    }, () => {
      const {items, active} = this.state;
      const item = items.find(item => item.path === active);
      if (item && item.preview) {
        item.preview
          .fetchIfNeededOrWait()
          .then(() => {
            if (item.preview.loaded && item.preview.value.url) {
              this.setState({
                preview: {
                  pending: false,
                  url: item.preview.value.url
                }
              });
            } else {
              throw new Error(item.preview.error || 'error loading image url');
            }
          })
          .catch(e => {
            this.setState({
              preview: {
                pending: false,
                error: `Preview not available: ${e.message}`
              }
            });
          });
      } else {
        this.setState({
          preview: {
            pending: false,
            error: 'Preview not available'
          }
        });
      }
    });
  };

  fetchPreviewItems = () => {
    const {file, storageId, dataStorageCache} = this.props;
    if (!file || !storageId || !dataStorageCache) {
      this.setState({
        items: [],
        active: undefined,
        preview: undefined,
        pending: false
      });
    } else {
      this.setState({
        items: [],
        active: undefined,
        preview: undefined,
        pending: true
      }, () => {
        const e = /^(.*\/)?([^\\/]+)\.vsi$/i.exec(file);
        if (e && e.length === 3) {
          const folder = `${e[1] || ''}${e[2]}`;
          const request = new DataStorageRequest(
            storageId,
            decodeURIComponent(folder),
            false,
            50
          );
          request
            .fetchPage()
            .then(() => {
              if (request.loaded) {
                const pathRegExp = new RegExp(`^${folder}\\/`, 'i');
                const items = ((request.value || {}).results || [])
                  .filter(item => /^file$/i.test(item.type) &&
                    pathRegExp.test(item.path)
                  )
                  .map(item => ({
                    ...item,
                    extension: (item.path || '').split('.').pop()
                  }))
                  .filter(item => /^(png|jpg|jpeg|tiff|gif|svg)$/i.test(item.extension))
                  .map(item => ({
                    ...item,
                    preview: dataStorageCache.getDownloadUrl(
                      storageId,
                      item.path
                    )
                  }));
                this.setState({
                  items,
                  pending: false
                }, () => this.onChangePreview(items.length > 0 ? items[0].path : undefined));
              } else {
                this.setState({
                  pending: false
                });
              }
            })
            .catch(() => {
              this.setState({
                pending: false
              });
            });
        } else {
          this.setState({
            pending: false
          });
        }
      });
    }
  };

  renderSlides = () => {
    const {
      active,
      items = []
    } = this.state;
    if (items.length === 0) {
      return (
        <div>
          Preview not available
        </div>
      );
    }
    if (items.length === 1 && active) {
      return null;
    }
    const getItemName = item => {
      const fileName = (
        (item.name || item.path || '').split('/').pop() || ''
      );
      const parts = fileName.split('.');
      if (parts.length > 1) {
        return parts.slice(0, -1).join('.');
      }
      return fileName;
    };
    return (
      <Tabs
        defaultActiveKey={active}
        onChange={this.onChangePreview}
        className={styles.tabs}
      >
        {
          items.map(item => (
            <Tabs.TabPane
              tab={getItemName(item)}
              key={item.path}
            />
          ))
        }
      </Tabs>
    );
  };

  renderPreview = () => {
    const {
      active,
      preview,
      items
    } = this.state;
    if (!preview || !items || !items.length) {
      return null;
    }
    const {
      pending,
      error,
      url
    } = preview;
    let content;
    if (pending) {
      content = (<i style={{color: '#999'}}>Loading...</i>);
    } else if (error) {
      content = (<span style={{color: '#999'}}>{error}</span>);
    } else if (!url) {
      content = (<span style={{color: '#999'}}>Preview not available</span>);
    } else {
      content = (
        <img
          src={url}
          alt={active}
        />
      );
    }
    return (
      <div className={styles.vsiContentPreview}>
        {content}
      </div>
    );
  };

  render () {
    const {
      className
    } = this.props;
    return (
      <div
        className={className}
      >
        {this.renderSlides()}
        {this.renderPreview()}
      </div>
    );
  }
}

VSIPreview.propTypes = {
  className: PropTypes.string,
  file: PropTypes.string,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default VSIPreview;
