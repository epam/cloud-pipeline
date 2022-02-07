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
import {observer} from 'mobx-react';
import {observable} from 'mobx';
import {
  Alert
} from 'antd';
import fetchHCSInfo from './utilities/fetch-hcs-info';
import HcsImageWellsSelector from './hcs-image-wells-selector';
import HcsImageFieldSelector from './hcs-image-field-selector';
import styles from './hcs-image.css';

const HCSImageViewer = window.HcsImageViewer;

@observer
class HcsImage extends React.PureComponent {
  state = {
    pending: false,
    sequencePending: false,
    error: undefined,
    sequenceId: undefined,
    wells: [],
    fields: [],
    wellId: undefined,
    imageId: undefined,
    plateWidth: 0,
    plateHeight: 0,
    wellWidth: 0,
    wellHeight: 0,
    sequences: []
  };

  @observable hcsInfo;
  @observable container;
  hcsImageViewer;

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

  prepare = () => {
    const {
      storageId,
      path
    } = this.props;
    if (this.props.storageId && this.props.path) {
      this.setState({
        sequencePending: false,
        pending: false,
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

  changeSequence = (sequenceId) => {
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
                const {wells = []} = sequenceInfo;
                this.setState({
                  sequencePending: false,
                  error: undefined,
                  sequenceId,
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
    if (HCSImageViewer && container !== this.container) {
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
      this.loadImage();
    }
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
      wellId,
      imageId,
      wells = [],
      fields = [],
      plateWidth,
      plateHeight,
      wellWidth,
      wellHeight
    } = this.state;
    const pending = hcsImagePending || sequencePending;
    const selectedWell = wells.find(o => o.id === wellId);
    return (
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
            styles.hcsImageRenderer
          }
        >
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
          <div
            className={styles.hcsImage}
            ref={this.init}
          >
            {'\u00A0'}
          </div>
        </div>
        <div
          className={
            classNames(
              styles.hcsImageControls,
              'cp-content-panel'
            )
          }
        >
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
          />
        </div>
      </div>
    );
  }
}

HcsImage.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  path: PropTypes.string
};

HcsImage.WellsSelector = HcsImageWellsSelector;

export default HcsImage;
