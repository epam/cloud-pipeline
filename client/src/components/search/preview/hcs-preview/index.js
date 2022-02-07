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
import {computed, observable} from 'mobx';
import {
  message
} from 'antd';

import HcsImage from '../../../special/hcs-image';
import LoadingView from '../../../special/LoadingView';
import S3Storage from '../../../../models/s3-upload/s3-storage';
import DataStorageRequest from '../../../../models/dataStorage/DataStoragePage';
import DataStorageItemContent from '../../../../models/dataStorage/DataStorageItemContent';
import roleModel from '../../../../utils/roleModel';
import styles from '../preview.css';

function generateTileSource (storageId, tilesFolder, options) {
  // eslint-disable-next-line
  return ``
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
          console.log('getFolderContents -> contents', items);
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

function readStorageFileJson (storage, path) {
  console.log('readStorageFileJson => path', path);
  return new Promise((resolve) => {
    const request = new DataStorageItemContent(storage, path);
    request
      .fetch()
      .then(() => {
        if (request.loaded) {
          const {
            content
          } = request.value;
          if (content) {
            try {
              resolve(JSON.parse(atob(content)));
            } catch (e) {
              // eslint-disable-next-line
              throw new Error(`Error reading content for path ${path} (storage #${storage}): ${e.message}`);
            }
          } else {
            throw new Error(`Empty content for path ${path} (storage #${storage})`);
          }
        } else {
          throw new Error(request.error);
        }
      })
      .catch(() => resolve());
  });
}

function getTilesFromFolder (storageId, folder) {
  return new Promise((resolve) => {
    getFolderContents(storageId, folder)
      .then(tiles => {
        if (tiles && tiles.length > 0) {
          readStorageFileJson(
            storageId,
            `${folder}/info.json`
          )
            .then(info => {
              resolve({
                folder,
                info
              });
            });
        } else {
          resolve(undefined);
        }
      });
  });
}

function getTiles (storageId, folders) {
  if (!folders || !folders.length) {
    return Promise.resolve(undefined);
  }
  const [folder, ...restFolders] = folders;
  return new Promise((resolve) => {
    getTilesFromFolder(storageId, folder)
      .then((tiles) => {
        if (tiles) {
          return Promise.resolve(tiles);
        } else {
          return getTiles(storageId, restFolders);
        }
      })
      .then(resolve);
  });
}

function getTilesInfo (file) {
  const e = /^(.*\/)?([^\\/]+)\.(hcs)$/i.exec(file);
  if (e && e.length === 4) {
    const filePathWithoutExtension = `${e[1] || ''}${e[2]}`;
    return {
      tilesFolders: [
        `${e[1] || ''}.hcsparser/${e[2] || ''}/Images`,
        `${filePathWithoutExtension}`
      ],
      folder: filePathWithoutExtension
    };
  }
  return undefined;
}

@inject('dataStorageCache', 'dataStorages', 'preferences')
@observer
class HCSPreview extends React.Component {
  state = {
    items: [],
    tiles: false,
    preview: undefined,
    active: undefined,
    pending: false,
    s3storageWrapperPending: false,
    s3storageWrapperError: undefined,
    shareUrl: undefined,
    showShareUrlModal: false
  };

  @observable s3Storage;
  map;
  pathElement;

  componentDidMount () {
    this.createS3Storage();
    this.fetchPreviewItems();
  }

  componentWillUnmount () {
    // this.resetSAViewerCameraUpdate();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.storageId !== this.props.storageId || prevProps.file !== this.props.file) {
      this.createS3Storage();
      this.fetchPreviewItems();
    }
  }

  @computed
  get storage () {
    const {storageId, dataStorages} = this.props;
    if (storageId && dataStorages.loaded) {
      return (dataStorages.value || []).find(s => +(s.id) === +storageId);
    }
    return undefined;
  }

  createS3Storage = () => {
    const wrapCreateStorageCredentials = storage => new Promise((resolve, reject) => {
      storage.updateCredentials()
        .then(() => {
          resolve(storage);
        })
        .catch(reject);
    });
    this.setState({
      s3storageWrapperError: undefined,
      s3storageWrapperPending: true
    }, () => {
      const {dataStorages, storageId} = this.props;
      dataStorages.fetchIfNeededOrWait()
        .then(() => {
          if (!this.s3Storage && this.storage?.type === 'S3') {
            const {delimiter, path} = this.storage;
            const storage = {
              id: storageId,
              path,
              delimiter,
              write: roleModel.writeAllowed(this.storage)
            };
            return wrapCreateStorageCredentials(new S3Storage(storage));
          } else {
            return Promise.resolve(this.s3Storage);
          }
        })
        .then((storage) => {
          if (storage) {
            this.s3Storage = storage;
          }
          this.setState({
            s3storageWrapperError: undefined,
            s3storageWrapperPending: false
          });
        })
        .catch(e => {
          message.error(e.message, 5);
          this.setState({
            s3storageWrapperError: e.message,
            s3storageWrapperPending: false
          });
        });
    });
  };

  reportPreviewLoaded = () => {
    const {onPreviewLoaded} = this.props;
    if (onPreviewLoaded) {
      const {
        tiles
      } = this.state;
      onPreviewLoaded({maximizedAvailable: !!tiles});
    }
  };

  fetchPreviewItems = () => {
    const {
      file,
      storageId,
      dataStorageCache,
      preferences
    } = this.props;
    if (!file || !storageId || !dataStorageCache) {
      this.setState({
        items: [],
        tiles: false,
        active: undefined,
        preview: undefined,
        pending: false
      }, this.reportPreviewLoaded);
    } else {
      this.setState({
        items: [],
        tiles: false,
        active: undefined,
        preview: undefined,
        pending: true
      }, () => {
        this.reportPreviewLoaded();
        const tilesInfo = getTilesInfo(file);
        console.log('fetchItems -> tilesInfo', tilesInfo);
        if (tilesInfo) {
          getTiles(storageId, tilesInfo.tilesFolders)
            .then(tiles => {
              if (tiles) {
                this.setState({
                  items: [],
                  tiles,
                  pending: false
                }, this.reportPreviewLoaded);
              } else {
                getFolderContents(storageId, tilesInfo.folder)
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
                    }, () => {
                      this.onChangePreview(items.length > 0 ? items[0].path : undefined);
                      this.reportPreviewLoaded();
                    });
                  });
              }
            });
        }
      });
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

  renderPreview = () => {
    const {
      active,
      preview,
      pending,
      items,
      tiles
    } = this.state;
    if (!preview || !items || !items.length || tiles) {
      return null;
    }
    const {
      pending: previewPending,
      error,
      url
    } = preview;
    let content;
    if (pending || previewPending) {
      content = (<i style={{color: '#999'}}>Loading...</i>);
    } else if (error) {
      content = (<span style={{color: '#999'}}>{error}</span>);
    } else if (!url) {
      content = (<span style={{color: '#999'}}>Preview not available</span>);
    } else {
      content = (
        <HcsImage />
      );
    }
    return (
      <div className={styles.vsiContentPreview}>
        {content}
      </div>
    );
  };

  render () {
    console.log('component state', this.state);
    const {
      className
    } = this.props;
    const {
      pending: loading,
      s3storageWrapperPending
    } = this.state;
    const pending = loading || s3storageWrapperPending;
    return (
      <div
        className={className}
      >
        {pending && (<LoadingView />)}
        {!pending && this.renderPreview()}
      </div>
    );
  }
}

HCSPreview.propTypes = {
  className: PropTypes.string,
  file: PropTypes.string,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  fullScreenAvailable: PropTypes.bool,
  shareAvailable: PropTypes.bool,
  x: PropTypes.number,
  y: PropTypes.number,
  zoom: PropTypes.number,
  roll: PropTypes.number,
  onCameraChanged: PropTypes.func,
  onPreviewLoaded: PropTypes.func,
  fullscreen: PropTypes.bool,
  onFullScreenChange: PropTypes.func
};

HCSPreview.defaultProps = {
  fullScreenAvailable: true,
  shareAvailable: true
};

export default HCSPreview;
// export {getTiles, getTilesInfo};
