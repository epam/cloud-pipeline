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
import {cellsAreEqual, ascSorter} from './utilities/cells-utilities';
import ViewerState from './utilities/viewer-state';
import SourceState from './utilities/source-state';
import {getWellMesh, getWellImageFromMesh} from './utilities/get-well-mesh';
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
    selectedSequenceTimePoints: [],
    selectedZCoordinates: [],
    selectedWells: [],
    selectedFields: []
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
    if (this.hcsInfo) {
      this.hcsInfo.destroy();
      this.hcsInfo = undefined;
    }
  }

  @computed
  get sequences () {
    if (!this.hcsInfo) {
      return [];
    }
    return (this.hcsInfo.sequences || []).map(s => s);
  }

  get selectedSequences () {
    const {selectedSequenceTimePoints = []} = this.state;
    const selectedSequencesIds = [
      ...new Set(selectedSequenceTimePoints.map(o => o.sequence))
    ];
    return this.sequences.filter(aSequence => selectedSequencesIds.includes(aSequence.id));
  }

  get selectedSequence () {
    return this.selectedSequences[0];
  }

  get selectedTimePoints () {
    const sequence = this.selectedSequence;
    if (sequence) {
      const {selectedSequenceTimePoints = []} = this.state;
      return selectedSequenceTimePoints
        .filter(o => o.sequence === sequence.id)
        .map(o => o.timePoint)
        .sort((a, b) => Number(a) - Number(b));
    }
    return [];
  }

  get selectedTimePoint () {
    return this.selectedTimePoints[0] || 0;
  }

  get selectedZCoordinate () {
    const [field] = this.selectedWellFields;
    if (field) {
      const {
        selectedZCoordinates = []
      } = this.state;
      const available = selectedZCoordinates
        .filter(z => Number(z) < field.depth)
        .sort(ascSorter);
      return available[0] || 0;
    }
    return 0;
  }

  get selectedWells () {
    const sequence = this.selectedSequence;
    if (sequence) {
      const {selectedWells = []} = this.state;
      const wellIsSelected = aWell => selectedWells
        .some(o => o.id === aWell.id);
      const {wells = []} = sequence;
      return wells
        .filter(wellIsSelected)
        .sort((a, b) => (a.x - b.x) || (a.y - b.y));
    }
    return [];
  }

  get selectedWell () {
    return this.selectedWells[0];
  }

  get selectedWellFields () {
    const well = this.selectedWell;
    if (well) {
      const {selectedFields = []} = this.state;
      const fieldIsSelected = aField => selectedFields
        .some(o => aField.id === o.id);
      return (well.images || [])
        .filter(fieldIsSelected)
        .sort((a, b) => (a.x - b.x) || (a.y - b.y));
    }
    return [];
  }

  get wellViewAvailable () {
    const sequence = this.selectedSequence;
    const well = this.selectedWell;
    return sequence &&
      well &&
      well.overviewOmeTiffFileName &&
      well.overviewOffsetsJsonFileName &&
      well.wellImageId;
  }

  get showEntireWell () {
    return this.wellViewAvailable && this.selectedWellFields.length > 1;
  }

  prepare = () => {
    const {
      storage,
      storageId,
      path
    } = this.props;
    if (this.hcsInfo) {
      this.hcsInfo.destroy();
      this.hcsInfo = undefined;
    }
    if ((storageId || storage) && path) {
      this.setState({
        sequencePending: false,
        pending: true,
        error: undefined,
        plateWidth: 0,
        plateHeight: 0,
        selectedSequenceTimePoints: [],
        selectedZCoordinates: []
      }, () => {
        HCSInfo.fetch({storageInfo: storage, storageId, path})
          .then(info => {
            const {
              sequences = [],
              width,
              height
            } = info;
            const [first] = sequences;
            const {
              sequence,
              timeSeries = []
            } = first || {};
            this.setState({
              pending: false,
              error: undefined,
              plateWidth: width,
              plateHeight: height
            }, () => {
              this.hcsInfo = info;
              this.hcsInfo
                .addURLsRegeneratedListener(this.loadImage);
              this.onChangeSequenceTimePoints([
                {
                  sequence,
                  timePoint: timeSeries.length > 0 ? timeSeries[0].id : 0
                }
              ]);
            });
          })
          .catch((error) => {
            this.setState({
              pending: false,
              error: error.message,
              plateWidth: 0,
              plateHeight: 0,
              selectedSequenceTimePoints: [],
              selectedZCoordinates: []
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
        selectedSequenceTimePoints: [],
        selectedZCoordinates: []
      }, () => {
        this.hcsInfo = undefined;
      });
    }
  };

  onChangeSequenceTimePoints = (selection = []) => {
    const currentSequence = this.selectedSequence;
    const currentSequenceId = currentSequence ? currentSequence.id : undefined;
    this.setState({
      selectedSequenceTimePoints: selection.slice()
    }, () => {
      const newSequence = this.selectedSequence;
      const newSequenceId = newSequence ? newSequence.id : undefined;
      const timePointId = this.selectedTimePoint;
      if (newSequenceId !== currentSequenceId) {
        this.changeSequence();
      } else if (this.hcsImageViewer) {
        this.hcsImageViewer.setGlobalTimePosition(timePointId);
      }
    });
  };

  onChangeZCoordinates = (selection = []) => {
    const currentZ = this.selectedZCoordinate;
    this.setState({
      selectedZCoordinates: selection.slice()
    }, () => {
      const newZ = this.selectedZCoordinate;
      if (newZ !== currentZ && this.hcsImageViewer) {
        this.hcsImageViewer.setGlobalZPosition(Number(newZ));
      }
    });
  };

  changeSequence = () => {
    const sequence = this.selectedSequence;
    if (sequence) {
      this.setState({
        sequencePending: true
      }, () => {
        sequence
          .fetch()
          .then(() => {
            const {wells = []} = sequence;
            this.setState({
              sequencePending: false,
              error: undefined
            }, () => {
              const firstWell = wells[0];
              if (firstWell) {
                this.changeWells([firstWell]);
              }
            });
          })
          .catch(e => {
            this.setState({
              error: e.message,
              sequencePending: false
            });
          });
      });
    }
  };

  changeWells = (wellsSelection = []) => {
    const currentWell = this.selectedWell;
    this.setState({
      selectedWells: wellsSelection
    }, () => {
      const well = this.selectedWell;
      if (well && !cellsAreEqual(currentWell, well)) {
        const {
          wellViewByDefault
        } = this.props;
        const {images = []} = well;
        const fields = wellViewByDefault ? images.slice() : images.slice(0, 1);
        this.changeWellImages(fields);
      }
    });
  };

  onMeshCellClick = (viewer, cell) => {
    const well = this.selectedWell;
    if (well) {
      const image = getWellImageFromMesh(well, cell);
      if (image) {
        this.changeWellImages([image]);
      }
    }
  };

  changeWellImages = (images = []) => {
    this.setState({
      selectedFields: images.slice()
    }, () => this.loadImage());
  };

  loadImage = () => {
    const sequence = this.selectedSequence;
    const well = this.selectedWell;
    const fields = this.selectedWellFields;
    if (
      sequence &&
      well &&
      fields.length > 0
    ) {
      let url = well.omeTiffFileName;
      let offsetsJsonUrl = well.offsetsJsonFileName;
      let {id} = fields[0];
      if (this.showEntireWell) {
        url = well.overviewOmeTiffFileName;
        offsetsJsonUrl = well.overviewOffsetsJsonFileName;
        id = well.wellImageId;
      }
      if (this.hcsImageViewer) {
        sequence.hcsURLsManager
          .setActiveURL(url, offsetsJsonUrl)
          .then(() => sequence.reportReadAccess())
          .then(() => this.hcsImageViewer.setData(
            sequence.hcsURLsManager.omeTiffURL,
            sequence.hcsURLsManager.offsetsJsonURL
          ))
          .then(() => {
            if (this.hcsImageViewer) {
              const imagePayload = {
                ID: id,
                imageTimePosition: this.selectedTimePoint || 0,
                imageZPosition: this.selectedZCoordinate,
                mesh: this.showEntireWell
                  ? getWellMesh(well, this.selectedWellFields)
                  : undefined
              };
              this.hcsImageViewer.setImage(imagePayload);
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
      this.hcsImageViewer.addEventListener(
        this.hcsImageViewer.Events.onCellClick,
        this.onMeshCellClick.bind(this)
      );
      this.hcsViewerState.attachToViewer(this.hcsImageViewer);
      this.hcsSourceState.attachToViewer(this.hcsImageViewer);
      this.loadImage();
    } else {
      this.hcsViewerState.detachFromViewer();
      this.hcsSourceState.detachFromViewer();
      this.hcsImageViewer = undefined;
    }
  };

  renderDetailsActions = (className = styles.detailsActions, handleClick = true) => {
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
  };

  showDetails = () => {
    this.setState({
      showDetails: true
    });
  };

  hideDetails = () => {
    this.setState({
      showDetails: false
    });
  };

  showConfiguration = () => {
    this.setState({
      showConfiguration: true
    });
  };

  hideConfiguration = () => {
    this.setState({
      showConfiguration: false
    });
  };

  toggleWellView = () => {
    if (this.showEntireWell) {
      this.setState({
        selectedFields: this.selectedWellFields.slice(0, 1)
      }, this.loadImage);
    } else if (this.wellViewAvailable && this.selectedWell) {
      const {images = []} = this.selectedWell;
      this.setState({
        selectedFields: images.slice()
      }, this.loadImage);
    }
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

  renderDownloadBtn (options = {}) {
    const {
      showTitle = false,
      buttonSize = 'small'
    } = options;
    const downloadAvailable = downloadCurrentTiffAvailable(this.hcsImageViewer);
    const handleClickDownload = () => downloadCurrentTiff(
      this.hcsImageViewer,
      {
        wellView: this.showEntireWell,
        wellId: this.selectedWell ? this.selectedWell.id : undefined
      }
    );
    const btnContent = showTitle
      ? 'Download current image'
      : (
        <Icon
          type={'camera'}
          className="cp-larger"
        />
      );
    return (
      <Button
        className={styles.action}
        disabled={!downloadAvailable}
        onClick={handleClickDownload}
        size={buttonSize}
      >
        {btnContent}
      </Button>
    );
  }

  renderConfigurationActions = () => {
    const {
      error,
      showConfiguration
    } = this.state;
    if (
      error ||
      !this.hcsViewerState ||
      !this.hcsViewerState.channels ||
      !this.hcsViewerState.channels.length
    ) {
      return null;
    }
    if (!showConfiguration) {
      return (
        <div
          className={styles.configurationActions}
        >
          {
            this.wellViewAvailable && (
              <Button
                className={styles.action}
                size="small"
                onClick={this.toggleWellView}
              >
                <Icon type={this.showEntireWell ? 'appstore' : 'appstore-o'} />
              </Button>
            )
          }
          {this.renderDownloadBtn()}
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
                value={this.showEntireWell ? 'enabled' : 'disabled'}
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
          {this.renderDownloadBtn({showTitle: true, buttonSize: 'default'})}
        </div>
      </Panel>
    );
  };

  renderViewer () {
    const {
      error,
      pending: hcsImagePending,
      sequencePending,
      showDetails
    } = this.state;
    const pending = hcsImagePending ||
      sequencePending ||
      !this.hcsImageViewer ||
      this.hcsSourceState.pending ||
      this.hcsViewerState.pending;
    return (
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
                this.renderDetailsActions(
                  styles.hiddenDetailsActions,
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
            : this.renderDetailsActions()
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
    );
  }

  renderSelectors () {
    const {
      plateWidth,
      plateHeight,
      selectedSequenceTimePoints = [],
      selectedZCoordinates = [],
      selectedWells = [],
      selectedFields = []
    } = this.state;
    const sequenceInfo = this.selectedSequence;
    const selectedWell = this.selectedWell;
    const selectedImage = this.selectedWellFields[0];
    if (
      sequenceInfo &&
      !sequenceInfo.error &&
      !sequenceInfo.hcsURLsManager.error &&
      selectedWell
    ) {
      return (
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
            title="Plate"
            cells={sequenceInfo.wells}
            selected={selectedWells}
            onChange={this.changeWells}
            width={HcsCellSelector.widthCorrection(plateWidth, sequenceInfo.wells)}
            height={HcsCellSelector.heightCorrection(plateHeight, sequenceInfo.wells)}
            showRulers
            searchPlaceholder="Search wells"
            showElementHint
          />
          <HcsCellSelector
            className={styles.selectorContainer}
            title={selectedWell.id}
            cells={selectedWell.images}
            selected={selectedFields}
            onChange={this.changeWellImages}
            gridMode="CROSS"
            width={
              HcsCellSelector.widthCorrection(selectedWell.width, selectedWell.images)
            }
            height={
              HcsCellSelector.heightCorrection(selectedWell.height, selectedWell.images)
            }
            scaleToROI
            radius={selectedWell.radius}
          />
          <HcsSequenceSelector
            sequences={this.sequences}
            selection={selectedSequenceTimePoints}
            onChange={this.onChangeSequenceTimePoints}
            multiple
          />
          <HcsZPositionSelector
            image={selectedImage}
            selection={selectedZCoordinates}
            onChange={this.onChangeZCoordinates}
          />
        </div>
      );
    }
    return null;
  }

  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <Provider
        hcsViewerState={this.hcsViewerState}
        hcsSourceState={this.hcsSourceState}
        hcsAnalysis={this.hcsAnalysis}
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
          {this.renderViewer()}
          {this.renderSelectors()}
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
  detailsButtonTitle: PropTypes.string,
  wellViewByDefault: PropTypes.bool
};

HcsImage.defaultProps = {
  detailsTitle: 'Details',
  detailsButtonTitle: 'Show details',
  wellViewByDefault: true
};

export default HcsImage;
