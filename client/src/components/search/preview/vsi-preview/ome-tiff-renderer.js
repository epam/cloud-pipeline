/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {observable} from 'mobx';
import {Provider, observer} from 'mobx-react';
import {Button, Icon} from 'antd';
import HCSImageViewer from '../../../special/hcs-image/hcs-image-viewer';
import {createObjectStorageWrapper} from '../../../../utils/object-storage';
import dataStorageAvailable from '../../../../models/dataStorage/DataStorageAvailable';
import ViewerState from '../../../special/hcs-image/utilities/viewer-state';
import SourceState from '../../../special/hcs-image/utilities/source-state';
import Channels from '../../../special/hcs-image/hcs-image-controls/channels';
import ColorMap from '../../../special/hcs-image/hcs-image-controls/color-map';
import LoadingView from '../../../special/LoadingView';
import auditStorageAccessManager from '../../../../utils/audit-storage-access';
import styles from './ome-tiff-renderer.css';

@observer
class OmeTiffRenderer extends React.Component {
  state = {
    pending: false,
    error: undefined,
    storage: undefined,
    url: undefined,
    offsets: undefined,
    attributesVisible: false,
    controlsVisible: false
  };

  @observable hcsViewerState = new ViewerState();
  @observable hcsSourceState = new SourceState();

  hcsImageViewer;

  componentDidMount () {
    this.fetchURLs();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.storageId !== this.props.storageId ||
      prevProps.omeTiffPath !== this.props.omeTiffPath ||
      prevProps.offsetsJsonPath !== this.props.offsetsJsonPath
    ) {
      this.fetchURLs();
    }
  }

  initialize = (container) => {
    if (HCSImageViewer && this.container !== container && container) {
      this.container = container;
      this.hcsImageViewer = new HCSImageViewer({
        container,
        className: 'ome-tiff-image',
        style: {
          width: '100%',
          height: '100%'
        },
        minZoomBackOff: 0.25,
        maxZoomBackOff: 0,
        defaultZoomBackOff: 0.25,
        overview: {
          position: 'bottom-left'
        }
      });
      this.hcsViewerState.attachToViewer(this.hcsImageViewer);
      this.hcsSourceState.attachToViewer(this.hcsImageViewer);
      this.loadImage();
    } else {
      this.hcsViewerState.detachFromViewer();
      this.hcsSourceState.detachFromViewer();
      this.hcsImageViewer = undefined;
    }
  };

  fetchURLsToken = 0;

  fetchURLs = () => {
    const {
      storageId,
      omeTiffPath,
      offsetsJsonPath
    } = this.props;
    this.fetchURLsToken += 1;
    const token = this.fetchURLsToken;
    if (storageId && omeTiffPath) {
      this.setState({
        pending: true
      }, async () => {
        const state = {
          pending: false
        };
        const commit = () => {
          if (token === this.fetchURLsToken) {
            this.setState(state, this.loadImage);
          }
        };
        try {
          await dataStorageAvailable.fetchIfNeededOrWait();
          const storage = await createObjectStorageWrapper(
            dataStorageAvailable.value,
            storageId
          );
          if (!storage) {
            throw new Error('Unknown storage identifier');
          }
          state.url = await storage.generateFileUrl(omeTiffPath);
          state.offsets = offsetsJsonPath
            ? (await storage.generateFileUrl(offsetsJsonPath))
            : undefined;
          auditStorageAccessManager.reportReadAccess(...[{
            storageId,
            path: omeTiffPath
          }, offsetsJsonPath ? {
            storageId,
            path: offsetsJsonPath
          } : undefined].filter(Boolean));
        } catch (error) {
          state.error = error.message;
        } finally {
          commit();
        }
      });
    } else {
      this.setState({
        pending: false,
        error: undefined,
        url: undefined,
        offsets: undefined
      });
    }
  };

  loadImage = () => {
    const {
      url,
      offsets
    } = this.state;
    if (this.hcsImageViewer && url && offsets) {
      this.hcsImageViewer.setData(url, offsets);
    }
  };

  renderAttributes = () => {
    const {
      children
    } = this.props;
    if (!children) {
      return null;
    }
    const showAttributes = () => this.setState({attributesVisible: true});
    const hideAttributes = () => this.setState({attributesVisible: false});
    const {
      attributesVisible
    } = this.state;
    return (
      <div
        className={styles.attributes}
      >
        {
          !attributesVisible && (
            <Button
              size="small"
              onClick={showAttributes}
            >
              Show attributes
            </Button>
          )
        }
        {
          attributesVisible && (
            <div
              className={
                classNames(
                  'cp-panel',
                  styles.panel
                )
              }
            >
              <div
                className={
                  classNames(
                    styles.header,
                    'cp-divider',
                    'bottom'
                  )
                }
              >
                <b>Attributes</b>
                <Icon
                  type="close"
                  className={styles.close}
                  onClick={hideAttributes}
                />
              </div>
              <div
                className={styles.content}
              >
                {children}
              </div>
            </div>
          )
        }
      </div>
    );
  };

  renderControls = () => {
    const {
      fullscreen,
      onChangeFullScreen,
      fullScreenAvailable
    } = this.props;
    const showControls = () => this.setState({controlsVisible: true});
    const hideControls = () => this.setState({controlsVisible: false});
    const {
      controlsVisible
    } = this.state;
    return (
      <div
        className={styles.controls}
      >
        {
          !controlsVisible && (
            <div>
              {
                fullScreenAvailable && typeof onChangeFullScreen === 'function' && (
                  <Button
                    size="small"
                    onClick={onChangeFullScreen}
                    style={{marginRight: 5}}
                  >
                    <Icon
                      type={fullscreen ? 'shrink' : 'arrows-alt'}
                      className="cp-larger"
                    />
                  </Button>
                )
              }
              <Button
                size="small"
                onClick={showControls}
              >
                <Icon
                  type="setting"
                  className="cp-larger"
                />
              </Button>
            </div>
          )
        }
        {
          controlsVisible && (
            <div
              className={
                classNames(
                  'cp-panel',
                  styles.panel
                )
              }
            >
              <div
                className={
                  classNames(
                    styles.header,
                    'cp-divider',
                    'bottom'
                  )
                }
              >
                <b>Configuration</b>
                <Icon
                  type="close"
                  className={styles.close}
                  onClick={hideControls}
                />
              </div>
              <div
                className={styles.content}
              >
                <ColorMap />
                <Channels
                  allowLockChannels={false}
                />
              </div>
            </div>
          )
        }
      </div>
    );
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      pending
    } = this.state;
    const loading = pending ||
      (this.hcsViewerState && this.hcsViewerState.pending) ||
      (this.hcsSourceState && this.hcsSourceState.pending);
    return (
      <Provider hcsViewerState={this.hcsViewerState}>
        <div
          className={
            classNames(
              className,
              styles.omeTiffRendererContainer
            )
          }
          style={style}
        >
          <div
            className={
              classNames(
                styles.omeTiffRenderer,
                'cp-dark-background'
              )
            }
            ref={this.initialize}
          >
            {'\u00A0'}
          </div>
          {
            loading && (<LoadingView />)
          }
          {this.renderAttributes()}
          {this.renderControls()}
        </div>
      </Provider>
    );
  }
}

OmeTiffRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  children: PropTypes.node,
  omeTiffPath: PropTypes.string,
  offsetsJsonPath: PropTypes.string,
  fullscreen: PropTypes.bool,
  onChangeFullScreen: PropTypes.func,
  fullScreenAvailable: PropTypes.bool
};

export default OmeTiffRenderer;
