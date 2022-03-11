/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer, Provider} from 'mobx-react';
import {computed, observable} from 'mobx';
import {Alert, Button, Icon, Radio} from 'antd';

import HCSImageViewer from './hcs-image-viewer';
import HCSInfo from './utilities/hcs-image-info';
import HcsCellSelector from './hcs-cell-selector';
import HcsSequenceSelector from './hcs-sequence-selector';
import ViewerState from './utilities/viewer-state';
import SourceState from './utilities/source-state';
import HcsImageControls from './hcs-image-controls';
import LoadingView from '../LoadingView';
import Panel from '../panel';
import HcsZPositionSelector from './hcs-z-position-selector';
import {
  downloadAvailable as downloadCurrentTiffAvailable,
  downloadCurrentTiff
} from './utilities/download-current-tiff';
import styles from './hcs-image.css';

@observer
class HcsImage extends React.PureComponent {
  state = {
    pending: false,
    sequencePending: false,
    error: undefined,
    sequenceId: undefined,
    timePointId: undefined, // zero-based
    wells: [],
    fields: [],
    wellId: undefined,
    imageId: undefined,
    wellImageId: undefined,
    plateWidth: 0,
    plateHeight: 0,
    wellWidth: 0,
    wellHeight: 0,
    sequences: [],
    showDetails: false,
    showConfiguration: false,
    showEntireWell: false
  };

  @observable hcsInfo;
  @observable container;
  @observable hcsViewerState = new ViewerState();
  @observable hcsSourceState = new SourceState();
  @observable hcsImageViewer;

  componentDidMount () {
    this.prepare();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.storageId !== this.props.storageId ||
      prevProps.storage !== this.props.storage ||
      prevProps.path !== this.props.path
    ) {
      this.prepare();
    }
  }

  componentWillUnmount () {
    this.container = undefined;
  }

  @computed
  get sequences () {
    if (!this.hcsInfo) {
      return [];
    }
    return (this.hcsInfo.sequences || []).map(s => s);
  }

  get wellViewAvailable () {
    const {wellImageId, sequenceId} = this.state;
    if (sequenceId) {
      const sequence = this.sequences.find(s => s.id === sequenceId);
      return sequence &&
        sequence.overviewOmeTiff &&
        sequence.overviewOffsetsJson &&
        wellImageId;
    }
    return false;
  }

  prepare = () => {
    const {
      storage,
      storageId,
      path
    } = this.props;
    if ((storageId || storage) && path) {
      this.setState({
        sequencePending: false,
        pending: true,
        error: undefined,
        wells: [],
        fields: [],
        plateWidth: 0,
        plateHeight: 0,
        wellWidth: 0,
        wellHeight: 0,
        imageId: undefined,
        wellId: undefined,
        sequenceId: undefined,
        sequences: []
      }, () => {
        HCSInfo.fetch({storageInfo: storage, storageId, path})
          .then(info => {
            const {
              sequences = [],
              width,
              height
            } = info;
            const sequencesIds = sequences.map(o => o.sequence);
            this.setState({
              pending: false,
              error: undefined,
              wells: [],
              fields: [],
              plateWidth: width,
              plateHeight: height,
              sequences: sequencesIds
            }, () => {
              this.hcsInfo = info;
              const {sequences} = this.state;
              this.changeSequence(sequences[0]);
            });
          })
          .catch((error) => {
            this.setState({
              pending: false,
              error: error.message,
              plateWidth: 0,
              plateHeight: 0,
              wellWidth: 0,
              wellHeight: 0,
              wells: [],
              fields: [],
              imageId: undefined,
              wellId: undefined,
              sequenceId: undefined,
              sequences: []
            }, () => {
              this.hcsInfo = undefined;
            });
          });
      });
    } else {
      this.setState({
        sequencePending: false,
        pending: false,
        error: undefined,
        plateWidth: 0,
        plateHeight: 0,
        wellWidth: 0,
        wellHeight: 0,
        wells: [],
        fields: [],
        imageId: undefined,
        wellId: undefined,
        sequenceId: undefined,
        sequences: []
      }, () => {
        this.hcsInfo = undefined;
      });
    }
  };

  changeTimePoint = (sequence, timePoint) => {
    const {
      timePointId: currentTimePointId,
      sequenceId
    } = this.state;
    const timePointId = timePoint
      ? timePoint.id
      : undefined;
    if (timePoint.id === currentTimePointId && sequence === sequenceId) {
      return;
    }
    if (sequence !== sequenceId) {
      this.changeSequence(sequence, timePointId);
    } else {
      this.setState({timePointId}, () => {
        if (this.hcsImageViewer) {
          this.hcsImageViewer.setGlobalTimePosition(timePointId);
        }
      });
    }
  };

  changeSequence = (sequenceId, timePointId) => {
    const {sequenceId: currentSequenceId} = this.state;
    if (currentSequenceId !== sequenceId) {
      if (this.hcsInfo) {
        const {sequences = []} = this.hcsInfo;
        const sequenceInfo = sequences.find(o => o.id === sequenceId);
        if (sequenceInfo) {
          this.setState({
            sequencePending: true
          }, () => {
            sequenceInfo
              .fetch()
              .then(() => sequenceInfo.resignDataURLs())
              .then(() => {
                const {wells = [], timeSeries = []} = sequenceInfo;
                const defaultTimePointId = timeSeries.length > 0
                  ? timeSeries[0].id
                  : 0;
                this.setState({
                  sequencePending: false,
                  error: undefined,
                  sequenceId,
                  timePointId: timePointId === undefined
                    ? defaultTimePointId
                    : timePointId,
                  wells,
                  showEntireWell: false
                }, () => {
                  const firstWell = wells[0];
                  if (firstWell) {
                    this.changeWell(firstWell);
                  }
                });
              })
              .catch(e => {
                this.setState({
                  error: e.message,
                  sequencePending: false,
                  wells: []
                });
              });
          });
        }
      }
    }
  };

  changeWell = ({x, y} = {}) => {
    const {
      sequenceId,
      wellId
    } = this.state;
    if (this.hcsInfo) {
      const sequence = (this.hcsInfo.sequences || []).find(s => s.id === sequenceId);
      if (sequence) {
        const {wells = []} = sequence;
        const well = wells.find(w => w.x === x && w.y === y);
        if (well && wellId !== well.id) {
          const {images = []} = well;
          const [firstImage] = images;
          this.setState({
            wellId: well.id,
            wellWidth: well.width,
            wellHeight: well.height,
            imageId: undefined,
            wellImageId: well.wellImageId,
            fields: images
          }, () => this.changeWellImage(firstImage, true));
        }
      }
    }
  };

  changeWellImage = ({x, y} = {}, keepShowEntireWell = false) => {
    const {
      sequenceId,
      wellId,
      imageId: currentImageId,
      showEntireWell
    } = this.state;
    if (this.hcsInfo) {
      const sequence = (this.hcsInfo.sequences || []).find(s => s.id === sequenceId);
      if (sequence) {
        const {wells = []} = sequence;
        const well = wells.find(w => w.id === wellId);
        if (well) {
          const {images = []} = well;
          const image = images.find(i => i.x === x && i.y === y);
          if (image && image.id !== currentImageId) {
            // todo: re-fetch signed urls here?
            this.setState({
              imageId: image.id,
              showEntireWell: keepShowEntireWell ? showEntireWell : false
            }, () => this.loadImage());
          }
        }
      }
    }
  };

  loadImage = () => {
    const {
      sequenceId,
      imageId,
      timePointId,
      wellImageId,
      showEntireWell
    } = this.state;
    if (this.hcsImageViewer && this.hcsInfo) {
      const z = this.hcsViewerState
        ? this.hcsViewerState.imageZPosition
        : 0;
      const {sequences = []} = this.hcsInfo;
      const sequence = sequences.find(s => s.id === sequenceId);
      if (sequence && sequence.omeTiff) {
        let url = sequence.omeTiff;
        let offsetsJsonUrl = sequence.offsetsJson;
        let id = imageId;
        if (this.wellViewAvailable && showEntireWell) {
          url = sequence.overviewOmeTiff;
          offsetsJsonUrl = sequence.overviewOffsetsJson;
          id = wellImageId;
        }
        this.hcsImageViewer.setData(url, offsetsJsonUrl)
          .then(() => {
            if (this.hcsImageViewer) {
              this.hcsImageViewer.setImage({
                ID: id,
                imageTimePosition: timePointId,
                imageZPosition: z
              });
            }
          });
      }
    }
  };

  init = (container) => {
    if (HCSImageViewer && container !== this.container && container) {
      this.container = container;
      this.hcsImageViewer = new HCSImageViewer({
        container,
        className: 'hcs-image',
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

  renderDetailsInfo = (className = styles.detailsInfoBtn, handleClick = true) => {
    const {
      children,
      detailsButtonTitle = 'Show details'
    } = this.props;
    if (children) {
      return (
        <Button
          size="small"
          className={className}
          onClick={handleClick ? this.showDetails : undefined}
        >
          {detailsButtonTitle}
        </Button>
      );
    } else {
      return null;
    }
  }

  showDetails = () => {
    this.setState({
      showDetails: true
    });
  }

  hideDetails = () => {
    this.setState({
      showDetails: false
    });
  }

  showConfiguration = () => {
    this.setState({
      showConfiguration: true
    });
  }

  hideConfiguration = () => {
    this.setState({
      showConfiguration: false
    });
  }

  toggleWellView = () => {
    const {showEntireWell} = this.state;
    this.setState({showEntireWell: !showEntireWell}, () => {
      this.loadImage();
    });
  };

  showDetailsPanel = () => {
    const {
      children,
      detailsTitle = 'Details'
    } = this.props;
    return (
      <Panel
        title={detailsTitle}
        className={styles.detailsPanel}
        onClose={this.hideDetails}
      >
        {children}
      </Panel>
    );
  }

  renderConfigurationActions = () => {
    const {
      error,
      showConfiguration,
      showEntireWell
    } = this.state;
    if (
      error ||
      !this.hcsViewerState ||
      !this.hcsViewerState.channels ||
      !this.hcsViewerState.channels.length
    ) {
      return null;
    }
    const downloadAvailable = downloadCurrentTiffAvailable(this.hcsImageViewer);
    if (!showConfiguration) {
      return (
        <div
          className={styles.configurationActions}
        >
          {this.wellViewAvailable && (
            <Button
              className={styles.action}
              size="small"
              onClick={this.toggleWellView}
            >
              {showEntireWell
                ? <Icon type="appstore" />
                : <Icon type="appstore-o" />
              }
            </Button>
          )}
          <Button
            className={styles.action}
            size="small"
            disabled={!downloadAvailable}
            onClick={() => downloadCurrentTiff(this.hcsImageViewer)}
          >
            <Icon
              type="camera"
              className="cp-larger"
            />
          </Button>
          <Button
            className={styles.action}
            size="small"
            onClick={this.showConfiguration}
          >
            <Icon
              type="setting"
              className="cp-larger"
            />
          </Button>
        </div>
      );
    }
    return (
      <Panel
        title="Settings"
        className={styles.configuration}
        onClose={this.hideConfiguration}
      >
        <HcsImageControls />
        <div className={styles.additionalConfigurationControls}>
          {this.wellViewAvailable && (
            <div className={styles.action}>
              <Radio.Group
                onChange={this.toggleWellView}
                value={showEntireWell ? 'enabled' : 'disabled'}
                className={styles.wellViewGroup}
              >
                <Radio.Button
                  value="enabled"
                  className={styles.wellViewButton}
                >
                  Well view
                </Radio.Button>
                <Radio.Button
                  value="disabled"
                  className={styles.wellViewButton}
                >
                  Field view
                </Radio.Button>
              </Radio.Group>
            </div>
          )}
          <Button
            className={styles.action}
            disabled={!downloadAvailable}
            onClick={() => downloadCurrentTiff(this.hcsImageViewer)}
          >
            Download current image
          </Button>
        </div>
      </Panel>
    );
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      error,
      pending: hcsImagePending,
      sequencePending,
      sequenceId,
      timePointId,
      wellId,
      imageId,
      wells = [],
      fields = [],
      plateWidth,
      plateHeight,
      wellWidth,
      wellHeight,
      showDetails,
      showEntireWell
    } = this.state;
    const pending = hcsImagePending ||
      sequencePending ||
      !this.hcsImageViewer ||
      this.hcsSourceState.pending ||
      this.hcsViewerState.pending;
    const {sequences = []} = this.hcsInfo || {};
    const sequenceInfo = sequences.find(o => o.id === sequenceId);
    const selectedWell = wells.find(o => o.id === wellId);
    return (
      <Provider
        hcsViewerState={this.hcsViewerState}
        hcsSourceState={this.hcsSourceState}
      >
        <div
          className={
            classNames(
              className,
              styles.hcsImageContainer
            )
          }
          style={style}
        >
          <div
            className={
              classNames(
                styles.hcsImageRenderer,
                'cp-dark-background'
              )
            }
          >
            {
              pending && (
                <LoadingView
                  className={styles.loadingView}
                />
              )
            }
            {
              error && (
                <div
                  className={styles.alertContainer}
                >
                  {
                    this.renderDetailsInfo(
                      styles.hiddenDetailsButton,
                      false
                    )
                  }
                  <Alert
                    className={styles.alert}
                    type="error"
                    message={error}
                  />
                </div>
              )
            }
            {
              !error && this.renderConfigurationActions()
            }
            {
              showDetails
                ? this.showDetailsPanel()
                : this.renderDetailsInfo()
            }
            <div
              className={
                classNames(
                  styles.hcsImage,
                  {
                    [styles.pending]: pending
                  }
                )
              }
              ref={this.init}
            >
              {'\u00A0'}
            </div>
          </div>
          {
            sequenceInfo && !sequenceInfo.error && (
              <div
                className={
                  classNames(
                    styles.hcsImageControls,
                    'cp-content-panel'
                  )
                }
              >
                <HcsCellSelector
                  className={styles.selectorContainer}
                  cells={wells}
                  selectedId={wellId}
                  onChange={this.changeWell}
                  width={HcsCellSelector.widthCorrection(plateWidth, wells)}
                  height={HcsCellSelector.heightCorrection(plateHeight, wells)}
                  title="Plate"
                  cellShape={HcsCellSelector.Shapes.circle}
                  showLegend
                />
                <HcsCellSelector
                  className={styles.selectorContainer}
                  cells={fields}
                  onChange={this.changeWellImage}
                  selectedId={imageId}
                  width={HcsCellSelector.widthCorrection(wellWidth, fields)}
                  height={HcsCellSelector.heightCorrection(wellHeight, fields)}
                  title={selectedWell ? selectedWell.id : undefined}
                  cellShape={HcsCellSelector.Shapes.rect}
                  gridShape={HcsCellSelector.Shapes.circle}
                  gridRadius={selectedWell && selectedWell.radius ? selectedWell.radius : undefined}
                  flipVertical
                  showLegend={false}
                  entireWellView={showEntireWell}
                />
                <HcsSequenceSelector
                  sequences={this.sequences}
                  selectedSequence={sequenceId}
                  selectedTimePoint={timePointId}
                  onChangeTimePoint={this.changeTimePoint}
                />
                <HcsZPositionSelector />
              </div>
            )
          }
        </div>
      </Provider>
    );
  }
}

HcsImage.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node,
  style: PropTypes.object,
  storage: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  path: PropTypes.string,
  detailsTitle: PropTypes.string,
  detailsButtonTitle: PropTypes.string
};

HcsImage.defaultProps = {
  detailsTitle: 'Details',
  detailsButtonTitle: 'Show details'
};

export default HcsImage;
