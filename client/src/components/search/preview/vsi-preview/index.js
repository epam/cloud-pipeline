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
  Icon,
  Popover,
  message
} from 'antd';
import classNames from 'classnames';
import html2canvas from 'html2canvas';
import FileSaver from 'file-saver';
import DataStorageRequest from '../../../../models/dataStorage/DataStoragePage';
import DataStorageItemContent from '../../../../models/dataStorage/DataStorageItemContent';
import {API_PATH, SERVER, PUBLIC_URL} from '../../../../config';
import styles from '../preview.css';
import './girder-mock/index';
import '../../../../staticStyles/sa-styles.css';
import LoadingView from '../../../special/LoadingView';

const {SA, SAM, $} = window;

const SA_CAMERA_CALLBACK_TIMEOUT = 1000;
const SA_CAMERA_CALLBACK_START_TIMEOUT = 2500;

function generateTileSource (storageId, tilesFolder, x, y, z) {
  // eslint-disable-next-line
  return `${SERVER + API_PATH}/datastorage/${storageId}/download?path=${tilesFolder}/${z}/${y}/${x}.jpg`;
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

function readStorageFileJson (storage, path) {
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

function getTiles (storageId, folder) {
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

@inject('dataStorageCache')
@observer
class VSIPreview extends React.Component {
  state = {
    items: [],
    tiles: false,
    preview: undefined,
    active: undefined,
    pending: false,
    fullscreen: false,
    shareUrl: undefined,
    showShareUrlModal: false
  };

  map;
  pathElement;

  componentDidMount () {
    this.fetchPreviewItems();
  }

  componentWillUnmount () {
    this.resetSAViewerCameraUpdate();
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
    if (this.saViewer) {
      this.saViewer = undefined;
      this.resetSAViewerCameraUpdate();
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
          getTiles(storageId, tilesFolder)
            .then(tiles => {
              if (tiles) {
                this.setState({
                  items: [],
                  tiles,
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

  saveImage = (data, opts) => {
    const {x = 0, y = 0, z = 0} = opts || {};
    const {file} = this.props;
    const fileName = (file || '').split('/').pop().split('.').slice(0, -1);
    const canvasElement = document.createElement('canvas');
    document.body.style.overflowY = 'hidden';
    document.body.appendChild(canvasElement);
    const {width, height} = data;
    canvasElement.width = width;
    canvasElement.height = height;
    const context = canvasElement.getContext('2d');
    context.fillRect(0, 0, width, height);
    context.fillStyle = 'white';
    context.putImageData(data, 0, 0);
    canvasElement.toBlob((blob) => {
      FileSaver.saveAs(blob, `${fileName}-${z}-${x}-${y}.png`);
      document.body.removeChild(canvasElement);
      document.body.style.overflowY = 'unset';
    });
  };

  resetSAViewerCameraUpdate = () => {
    if (this.saViewerCameraCallbackTimer) {
      clearInterval(this.saViewerCameraCallbackTimer);
      this.saViewerCameraCallbackTimer = undefined;
    }
  };

  startSAViewerCameraUpdate = () => {
    this.resetSAViewerCameraUpdate();
    const {onCameraChanged} = this.props;
    if (!onCameraChanged) {
      return;
    }
    let previous = {};
    const callback = () => {
      if (!this.saViewer) {
        this.resetSAViewerCameraUpdate();
      }
      const {
        RollTarget: roll,
        ZoomTarget: zoom
      } = this.saViewer;
      const [x, y] = this.saViewer.MainView.Camera.GetWorldFocalPoint();
      if (
        previous.roll !== roll ||
        previous.zoom !== zoom ||
        previous.x !== x ||
        previous.y !== y
      ) {
        previous = {
          zoom,
          roll,
          x,
          y
        };
        onCameraChanged({...previous});
      }
    };
    this.saViewerCameraCallbackTimer = setInterval(
      callback,
      SA_CAMERA_CALLBACK_TIMEOUT
    );
  };

  generateShareUrl = () => {
    if (!this.saViewer) {
      return;
    }
    const {
      RollTarget: roll,
      ZoomTarget: zoom
    } = this.saViewer;
    const [x, y] = this.saViewer.MainView.Camera.GetWorldFocalPoint();
    const {
      storageId,
      file
    } = this.props;
    const query = [
      `storage=${storageId}`,
      `file=${file}`,
      `zoom=${zoom}`,
      `roll=${roll}`,
      `x=${x}`,
      `y=${y}`
    ];
    return `${PUBLIC_URL || ''}/#/wsi?${query.join('&')}`;
  };

  openShareUrlModal = (e) => {
    e && e.stopPropagation();
    const url = this.generateShareUrl();
    if (url) {
      this.setState({shareUrl: url});
    }
  };

  closeShareUrlModal = () => {
    this.setState({shareUrl: undefined});
  };

  shareUrlVisibilityChanged = (visible) => {
    if (visible) {
      this.openShareUrlModal();
    } else {
      this.closeShareUrlModal();
    }
  };

  onChangeShareUrl = (e) => {
    this.setState({shareUrl: e.target.value});
  };

  renderShareUrl = () => {
    const {shareUrl} = this.state;
    const initializePathElement = element => {
      this.pathElement = element;
    };
    const copy = (e) => {
      e.stopPropagation();
      e.preventDefault();
      if (this.pathElement) {
        const range = document.createRange();
        window.getSelection().removeAllRanges();
        range.selectNode(this.pathElement);
        window.getSelection().addRange(range);
        if (document.execCommand('copy')) {
          message.info('Copied to clipboard', 3);
          window.getSelection().removeAllRanges();
        }
      }
    };
    return (
      <div className={styles.shareUrlContainer}>
        <span>Copy file path:</span>
        <div className={styles.inputRow}>
          <textarea
            wrap="off"
            className={styles.shareUrl}
            ref={initializePathElement}
            value={shareUrl}
            onChange={this.onChangeShareUrl}
          />
          <div
            className={styles.shareUrlButton}
            onClick={copy}
          >
            <Icon type="copy" />
          </div>
        </div>
      </div>
    );
  };

  renderTiles = () => {
    const {
      storageId,
      fullScreenAvailable,
      shareAvailable,
      x,
      y,
      zoom,
      roll
    } = this.props;
    const {
      tiles,
      fullscreen,
      shareUrl
    } = this.state;
    if (!tiles || !storageId) {
      return null;
    }
    const initializeTiles = (element) => {
      if (element && !this.saViewer) {
        const {
          width = 10000,
          height = 10000,
          minLevel = 0,
          maxLevel = 5,
          bounds = [0, width - 1, 0, height - 1],
          ...rest
        } = tiles.info || {};
        const viewers = SA.SAViewer($(element),
          {
            zoomWidget: true,
            drawWidget: true,
            prefixUrl: `${PUBLIC_URL}/slideatlas-viewer/img/`,
            tileSource: {
              height,
              width,
              bounds,
              minLevel,
              maxLevel,
              ...rest,
              getTileUrl: function (level, x, y) {
                return generateTileSource(storageId, tiles.folder, x, y, level);
              }
            }
          });
        this.saViewer = viewers[0].saViewer;
        this.saViewer.ShareTab.hide();
        // eslint-disable-next-line
        const panel = new SAM.LayerPanel(this.saViewer, `${storageId}/${tiles.folder}`);
        if (zoom || roll || x || y) {
          const zoomTarget = zoom || this.saViewer.ZoomTarget;
          const rollTarget = roll || this.saViewer.RollTarget;
          const current = this.saViewer.MainView.Camera.GetWorldFocalPoint();
          const center = [
            x || current[0] || 0,
            y || current[1] || 0
          ];
          this.saViewer.AnimateCamera(center, rollTarget, zoomTarget);
        }
        setTimeout(
          () => this.startSAViewerCameraUpdate(),
          SA_CAMERA_CALLBACK_START_TIMEOUT
        );
      } else if (this.saViewer) {
        $(window).trigger('resize');
      }
    };
    const goFullScreen = () => {
      this.setState({
        fullscreen: !fullscreen
      });
    };
    const capture = () => {
      const [x, y] = this.saViewer.MainView.Camera.GetWorldFocalPoint();
      const z = this.saViewer.ZoomTarget;
      const hideElements = this.saViewer.GetDiv()
        .children('div:not(.sa-resize.sa-view-canvas-div)');
      hideElements
        .each(function () {
          $(this).addClass(styles.vsiSaViewHidden);
        });
      html2canvas(this.saViewer.GetDiv()[0])
        .then((canvas) => {
          const ctx = canvas.getContext('2d');
          const data = ctx.getImageData(0, 0, canvas.width, canvas.height);
          return this.saveImage(data, {x: Math.floor(x), y: Math.floor(y), z: Math.floor(z)});
        })
        .catch(e => {
          console.warn(`Error creating screenshot: ${e.message}`);
        })
        .then(() => {
          hideElements
            .each(function () {
              $(this).removeClass(styles.vsiSaViewHidden);
            });
        });
    };
    return (
      <div
        onClick={() => {
          if (shareUrl !== null || shareUrl !== undefined) {
            this.closeShareUrlModal();
          }
        }}
        className={
          classNames(
            styles.vsiContentPreview,
            styles.tiles,
            {[styles.fullscreen]: fullscreen}
          )
        }
      >
        {/* eslint-disable-next-line */}
        <div
          ref={initializeTiles}
          style={{
            width: '100%',
            height: '100%'
          }}
        >
        </div>
        <div
          className={styles.vsiPreviewButtonContainer}
        >
          {
            fullScreenAvailable && (
              <Button
                id="vsi-preview-fullscreen-button"
                className={styles.vsiPreviewButton}
                onClick={goFullScreen}
              >
                <Icon type={fullscreen ? 'shrink' : 'arrows-alt'} />
              </Button>
            )
          }
          <Button
            id="vsi-preview-capture-button"
            className={styles.vsiPreviewButton}
            onClick={capture}
          >
            <Icon type="camera" />
          </Button>
          {
            shareAvailable && (
              <Popover
                visible={shareUrl !== undefined && shareUrl !== null}
                trigger={['click']}
                title={false}
                content={this.renderShareUrl()}
                onVisibleChange={this.shareUrlVisibilityChanged}
                overlayClassName={styles.shareUrlPopover}
                align={fullscreen ? {
                  points: ['tl', 'bl'],
                  offset: ['-10px']
                } : {}}
                placement="bottom"
              >
                <Button
                  id="vsi-preview-share-button"
                  className={styles.vsiPreviewButton}
                  onClick={this.openShareUrlModal}
                >
                  <Icon type="export" />
                </Button>
              </Popover>
            )
          }
        </div>
      </div>
    );
  };

  render () {
    const {
      className
    } = this.props;
    const {pending} = this.state;
    return (
      <div
        className={className}
      >
        {pending && (<LoadingView />)}
        {!pending && this.renderSlides()}
        {!pending && this.renderPreview()}
        {!pending && this.renderTiles()}
      </div>
    );
  }
}

VSIPreview.propTypes = {
  className: PropTypes.string,
  file: PropTypes.string,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  fullScreenAvailable: PropTypes.bool,
  shareAvailable: PropTypes.bool,
  x: PropTypes.number,
  y: PropTypes.number,
  zoom: PropTypes.number,
  roll: PropTypes.number,
  onCameraChanged: PropTypes.func
};

VSIPreview.defaultProps = {
  fullScreenAvailable: true,
  shareAvailable: true
};

export default VSIPreview;
