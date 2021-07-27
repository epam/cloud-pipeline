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
  Button,
  Tabs,
  Icon
} from 'antd';
import classNames from 'classnames';
import Leaflet from 'leaflet/dist/leaflet';
import DataStorageRequest from '../../../../models/dataStorage/DataStoragePage';
import {API_PATH, SERVER} from '../../../../config';
import styles from '../preview.css';
import 'slideatlas-viewer/dist/sa-lib';
import 'slideatlas-viewer/dist/sa.max';
import 'leaflet/dist/leaflet.css';
import 'slideatlas-viewer/css/saViewer.css';
import 'slideatlas-viewer/css/main.css';
import 'slideatlas-viewer/css/viewer.css';

console.log(SA);

function generateTileSource (storageId, tilesFolder) {
  // eslint-disable-next-line
  return `${SERVER + API_PATH}/datastorage/${storageId}/download?path=${tilesFolder}/{z}/{y}/{x}.jpg`;
}

function getFolderContents (storageId, folder) {
  return new Promise((resolve) => {
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
            .filter(item => pathRegExp.test(item.path));
          resolve(items);
        } else {
          resolve([]);
        }
      })
      .catch(() => {
        resolve([]);
      });
  });
}

@inject('dataStorageCache')
@observer
class VSIPreview extends React.Component {
  state = {
    items: [],
    tiles: false,
    preview: undefined,
    active: undefined,
    pending: false,
    fullscreen: false
  };

  map;

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
    if (this.map) {
      this.map.remove();
      this.map = undefined;
    }
    if (!file || !storageId || !dataStorageCache) {
      this.setState({
        items: [],
        tiles: false,
        active: undefined,
        preview: undefined,
        pending: false,
        fullscreen: false
      });
    } else {
      this.setState({
        items: [],
        tiles: false,
        active: undefined,
        preview: undefined,
        pending: true,
        fullscreen: false
      }, () => {
        const e = /^(.*\/)?([^\\/]+)\.(vsi|mrxs)$/i.exec(file);
        if (e && e.length === 4) {
          const tilesFolder = `${e[1] || ''}${e[2]}.tiles`;
          const folder = `${e[1] || ''}${e[2]}`;
          getFolderContents(storageId, tilesFolder)
            .then(tiles => {
              if (tiles.length > 0) {
                this.setState({
                  items: [],
                  tiles: tilesFolder,
                  pending: false
                });
              } else {
                getFolderContents(storageId, folder)
                  .then(items => {
                    const files = items
                      .filter(item => /^file$/i.test(item.type))
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
                      items: files,
                      tiles: false,
                      pending: false
                    }, () => this.onChangePreview(items.length > 0 ? items[0].path : undefined));
                  });
              }
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
      items = [],
      tiles
    } = this.state;
    if (tiles) {
      return null;
    }
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
      items,
      tiles
    } = this.state;
    if (!preview || !items || !items.length || tiles) {
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

  renderTiles = () => {
    const {
      storageId
    } = this.props;
    const {
      tiles,
      fullscreen
    } = this.state;
    if (!tiles || !storageId) {
      return null;
    }
    const initializeTiles = (element) => {
      if (element && !this.map) {
        this.map = Leaflet.map(
          element,
          {
            center: [0, 0],
            zoom: 0
          }
        );
        Leaflet.tileLayer(
          generateTileSource(storageId, tiles),
          {
            noWrap: true
          }
        ).addTo(this.map);
      } else if (this.map) {
        this.map.invalidateSize();
      }
    };
    const goFullScreen = () => {
      this.setState({
        fullscreen: !fullscreen
      });
    };
    return (
      <div
        className={
          classNames(
            styles.vsiContentPreview,
            styles.tiles,
            {[styles.fullscreen]: fullscreen}
          )
        }
      >
        <div
          ref={initializeTiles}
          style={{
            width: '100%',
            height: '100%'
          }}
        >
          {'\u00A0'}
        </div>
        <Button
          id="vsi-preview-fullscreen-button"
          className={styles.leafletFullscreenButton}
          onClick={goFullScreen}
        >
          <Icon type={fullscreen ? 'shrink' : 'arrows-alt'} />
        </Button>
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
        {this.renderTiles()}
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
