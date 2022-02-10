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
import {Alert, Button, Icon} from 'antd';

import fetchHCSInfo from './utilities/fetch-hcs-info';
import HcsImageWellsSelector from './hcs-image-wells-selector';
import HcsImageFieldSelector from './hcs-image-field-selector';
import ViewerState from './utilities/viewer-state';
import SourceState from './utilities/source-state';
import HcsImageControls from './hcs-image-controls';
import HcsSequenceSelector from './hcs-sequence-selector';
import styles from './hcs-image.css';
import LoadingView from "../LoadingView";

const HCSImageViewer = window.HcsImageViewer;

@observer
class HcsImage extends React.PureComponent {
  state = {
    pending: false,
    sequencePending: false,
    error: undefined,
    sequenceId: undefined,
    timepointId: undefined,
    wells: [],
    fields: [],
    wellId: undefined,
    imageId: undefined,
    plateWidth: 0,
    plateHeight: 0,
    wellWidth: 0,
    wellHeight: 0,
    sequences: [],
    showDetails: false
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

  prepare = () => {
    const {
      storageId,
      path
    } = this.props;
    if (this.props.storageId && this.props.path) {
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
        fetchHCSInfo({storageId, path})
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

  changeTimepoint = (sequence, timepoint) => {
    const {
      timepointId,
      sequenceId
    } = this.state;
    if (timepoint === timepointId && sequence === sequenceId) {
      return;
    }
    if (sequence !== sequenceId) {
      this.changeSequence(sequence, timepoint);
    } else {
      this.setState({timepointId: timepoint});
    }
  };

  changeSequence = (sequenceId, timepointId) => {
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
              .then(() => Promise.all([
                sequenceInfo.generateOMETiffURL(),
                sequenceInfo.generateOffsetsJsonURL()
              ]))
              .then(() => {
                const {wells = [], timeSeries = []} = sequenceInfo;
                this.setState({
                  sequencePending: false,
                  error: undefined,
                  sequenceId,
                  timepointId: timepointId || timeSeries[0],
                  wells
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
            fields: images
          }, () => this.changeWellImage(firstImage));
        }
      }
    }
  };

  changeWellImage = ({x, y} = {}) => {
    const {
      sequenceId,
      wellId,
      imageId: currentImageId
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
              imageId: image.id
            }, () => this.loadImage());
          }
        }
      }
    }
  };

  loadImage = () => {
    const {
      sequenceId,
      imageId
    } = this.state;
    if (this.hcsImageViewer && this.hcsInfo) {
      const {sequences = []} = this.hcsInfo;
      const sequence = sequences.find(s => s.id === sequenceId);
      if (sequence && sequence.omeTiff) {
        const url = sequence.omeTiff;
        const offsetsJsonUrl = sequence.offsetsJson;
        this.hcsImageViewer.setData(url, offsetsJsonUrl)
          .then(() => this.hcsImageViewer.setImage({ID: imageId}));
      }
    }
  };

  init = (container) => {
    if (HCSImageViewer && container !== this.container && container) {
      this.container = container;
      const {Viewer} = HCSImageViewer;
      this.hcsImageViewer = new Viewer({
        container,
        verbose: true,
        className: 'hcs-image',
        style: {
          width: '100%',
          height: '100%'
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

  renderDetailsInfo = () => {
    if (this.props.children && !this.state.error) {
      return (
        <Button
          type="primary"
          shape="circle"
          icon="info"
          size="small"
          className={styles.detailsInfoBtn}
          onClick={this.showDetails}
        />
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

  showDetailsPanel = () => {
    return (
      <div className={classNames(styles.detailsPanel, 'cp-panel-card')}>
        <Icon
          type="close"
          size="small"
          onClick={this.hideDetails}
        />
        {this.props.children}
      </div>
    );
  }

  render () {
    const {
      className,
      style,
      showWellSelectors
    } = this.props;
    const {
      error,
      pending: hcsImagePending,
      sequencePending,
      sequenceId,
      timepointId,
      wellId,
      imageId,
      wells = [],
      fields = [],
      plateWidth,
      plateHeight,
      wellWidth,
      wellHeight,
      showDetails
    } = this.state;
    const pending = hcsImagePending ||
      sequencePending ||
      !this.hcsImageViewer ||
      this.hcsSourceState.pending ||
      this.hcsViewerState.pending;
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
                  <Alert
                    type="error"
                    message={error}
                  />
                </div>
              )
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
            showWellSelectors && (
              <div
                className={
                  classNames(
                    styles.hcsImageControls,
                    'cp-content-panel'
                  )
                }
              >
                <HcsImageControls />
                <HcsSequenceSelector
                  sequences={this.sequences}
                  selectedSequence={sequenceId}
                  selectedTimepoint={timepointId}
                  onChangeTimepoint={this.changeTimepoint}
                />
                <HcsImageWellsSelector
                  style={{
                    minWidth: 200
                  }}
                  wells={wells}
                  onChange={this.changeWell}
                  selectedWell={wellId}
                  width={plateWidth}
                  height={plateHeight}
                />
                <HcsImageFieldSelector
                  style={{
                    minWidth: 200
                  }}
                  fields={fields}
                  onChange={this.changeWellImage}
                  selectedField={imageId}
                  width={wellWidth}
                  height={wellHeight}
                  wellName={selectedWell ? `Well ${selectedWell.x}_${selectedWell.y}` : undefined}
                  wellRadius={selectedWell && selectedWell.radius ? selectedWell.radius : undefined}
                />
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
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  path: PropTypes.string,
  showWellSelectors: PropTypes.bool
};

HcsImage.defaultProps = {
  showWellSelectors: true
};

HcsImage.WellsSelector = HcsImageWellsSelector;

export default HcsImage;
