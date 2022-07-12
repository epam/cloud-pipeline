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
import {getWellMesh, getWellImageFromMesh} from './utilities/get-well-mesh';
import HcsImageControls from './hcs-image-controls';
import LoadingView from '../LoadingView';
import Panel from '../panel';
import HcsZPositionSelector from './hcs-z-position-selector';
import {
  downloadAvailable as downloadCurrentTiffAvailable,
  downloadCurrentTiff
} from './utilities/download-current-tiff';
import HcsImageAnalysis from './hcs-image-analysis';
import AnalysisOutput from '../cellprofiler/components/analysis-output';
import {Analysis} from '../cellprofiler/model/analysis';
import roleModel from '../../../utils/roleModel';
import styles from './hcs-image.css';
import ObjectsOutline from '../cellprofiler/components/objects-outline';
import {cellsAreEqual, ascSorter} from './utilities/cells-utilities';

@observer
class HcsImage extends React.PureComponent {
  state = {
    pending: false,
    sequencePending: false,
    error: undefined,
    plateWidth: 0,
    plateHeight: 0,
    showDetails: false,
    showConfiguration: false,
    showAnalysis: false,
    showAnalysisResults: false,
    batchAnalysis: false,
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
  @observable hcsAnalysis = new Analysis();

  componentDidMount () {
    this.hcsAnalysis.setCurrentUser(this.props.authenticatedUserInfo);
    this.hcsAnalysis.addEventListener(Analysis.Events.analysisDone, this.onAnalysisDone);
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
    this.hcsAnalysis.removeEventListeners(Analysis.Events.analysisDone, this.onAnalysisDone);
    this.hcsAnalysis.destroy();
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
        .some(o => Number(o.x) === Number(aWell.x) && Number(o.y) === Number(aWell.y));
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
        .some(o => Number(o.x) === Number(aField.x) && Number(o.y) === Number(aField.y));
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
      sequence.overviewOmeTiff &&
      sequence.overviewOffsetsJson &&
      well &&
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
    const currentTimePointId = this.selectedTimePoint;
    this.setState({
      selectedSequenceTimePoints: selection.slice()
    }, () => {
      const newSequence = this.selectedSequence;
      const newSequenceId = newSequence ? newSequence.id : undefined;
      const timePointId = this.selectedTimePoint;
      if (timePointId === currentTimePointId && currentSequenceId === newSequenceId) {
        return;
      }
      if (newSequenceId !== currentSequenceId) {
        this.changeSequence();
      } else {
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
      if (newZ === currentZ || !this.hcsImageViewer) {
        return;
      }
      this.hcsImageViewer.setGlobalZPosition(Number(newZ));
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
          .then(() => sequence.resignDataURLs())
          .then(() => sequence.fetchMetadata())
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
    if (this.hcsImageViewer) {
      const sequence = this.selectedSequence;
      const well = this.selectedWell;
      const fields = this.selectedWellFields;
      if (
        sequence &&
        sequence.omeTiff &&
        well &&
        fields.length > 0
      ) {
        let url = sequence.omeTiff;
        let offsetsJsonUrl = sequence.offsetsJson;
        let {id} = fields[0];
        if (this.showEntireWell) {
          url = sequence.overviewOmeTiff;
          offsetsJsonUrl = sequence.overviewOffsetsJson;
          id = well.wellImageId;
        }
        this.hcsImageViewer.setData(url, offsetsJsonUrl)
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

  onAnalysisDone = () => {
    const resultsAvailable = !!this.hcsAnalysis && !!this.hcsAnalysis.analysisOutput;
    this.setState({
      showAnalysisResults: resultsAvailable
    });
  };

  loadImageForAnalysis = () => {
    if (this.hcsInfo && this.hcsAnalysis && this.hcsImageViewer) {
      const viewerState = this.hcsImageViewer.viewerState;
      const {
        globalSelection = {}
      } = viewerState || {};
      const {
        z = 0
      } = globalSelection;
      const sequence = this.selectedSequence;
      const wells = this.selectedWells;
      const fields = this.selectedWellFields;
      const timePoints = this.selectedTimePoints;
      if (
        sequence &&
        sequence.sourceDirectory &&
        fields.length > 0
      ) {
        const analysisPath = sequence.sourceDirectory;
        this.hcsAnalysis.updatePhysicalSize(
          fields[0].physicalSize,
          fields[0].unit
        );
        this.hcsAnalysis.changeFile({
          sourceDirectory: analysisPath,
          images: fields.map(field => field.fieldID),
          wells: wells,
          zCoordinates: [z + 1],
          timePoints: timePoints.map(t => t + 1),
          channels: fields[0].channels || []
        });
      }
    }
  }

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
      this.hcsImageViewer.addEventListener(
        this.hcsImageViewer.Events.viewerStateChanged,
        this.loadImageForAnalysis.bind(this)
      );
      this.hcsViewerState.attachToViewer(this.hcsImageViewer);
      this.hcsSourceState.attachToViewer(this.hcsImageViewer);
      this.loadImage();
    } else {
      this.hcsViewerState.detachFromViewer();
      this.hcsSourceState.detachFromViewer();
      this.hcsImageViewer = undefined;
    }
    this.hcsAnalysis.hcsImageViewer = this.hcsImageViewer;
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

  toggleAnalysis = () => {
    const {showAnalysis} = this.state;
    this.setState({
      showAnalysis: !showAnalysis
    });
  };

  toggleBatchAnalysis = (batchAnalysis) => {
    this.setState({
      batchAnalysis
    });
  };

  toggleAnalysisResults = (visible) => {
    const {showAnalysisResults} = this.state;
    this.setState({
      showAnalysisResults: visible === undefined ? !showAnalysisResults : visible
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

  renderConfigurationActions = () => {
    const {
      error,
      showConfiguration,
      showAnalysis
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
    const analysisAvailable = this.hcsAnalysis && this.hcsAnalysis.available;
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
          {
            analysisAvailable && (
              <Button
                className={styles.action}
                size="small"
                onClick={this.toggleAnalysis}
                type={showAnalysis ? 'primary' : 'default'}
              >
                <Icon
                  type="api"
                  className="cp-larger"
                />
              </Button>
            )
          }
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
        <ObjectsOutline />
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

  renderHcsAnalysisResults = () => {
    const {
      showAnalysis,
      showAnalysisResults
    } = this.state;
    if (
      showAnalysis &&
      showAnalysisResults &&
      this.hcsAnalysis &&
      this.hcsAnalysis.available &&
      this.hcsAnalysis.analysisOutput
    ) {
      return (
        <div
          className={styles.hcsImageAnalysisResults}
        >
          <div
            className={
              classNames(
                styles.hcsImageAnalysisResultsBackground,
                'cp-panel'
              )
            }
          >
            {'\u00A0'}
          </div>
          <AnalysisOutput
            className={styles.hcsImageAnalysisResultsContent}
            analysis={this.hcsAnalysis}
            onClose={() => this.toggleAnalysisResults(false)}
          />
        </div>
      );
    }
    return null;
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
      plateWidth,
      plateHeight,
      showDetails,
      showAnalysis,
      showAnalysisResults,
      batchAnalysis,
      selectedSequenceTimePoints = [],
      selectedZCoordinates = [],
      selectedWells = [],
      selectedFields = []
    } = this.state;
    const pending = hcsImagePending ||
      sequencePending ||
      !this.hcsImageViewer ||
      this.hcsSourceState.pending ||
      this.hcsViewerState.pending;
    const sequenceInfo = this.selectedSequence;
    const selectedWell = this.selectedWell;
    const selectedImage = this.selectedWellFields[0];
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
          {
            showAnalysis && (
              <HcsImageAnalysis
                className={styles.analysis}
                onToggleResults={this.toggleAnalysisResults}
                resultsVisible={showAnalysisResults}
                batchMode={batchAnalysis}
                toggleBatchMode={this.toggleBatchAnalysis}
              />
            )
          }
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
            {this.renderHcsAnalysisResults()}
          </div>
          {
            sequenceInfo && !sequenceInfo.error && selectedWell && (
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
                  cells={sequenceInfo.wells}
                  selected={selectedWells}
                  onChange={this.changeWells}
                  width={HcsCellSelector.widthCorrection(plateWidth, sequenceInfo.wells)}
                  height={HcsCellSelector.heightCorrection(plateHeight, sequenceInfo.wells)}
                  title="Plate"
                  cellShape={HcsCellSelector.Shapes.circle}
                  showLegend
                  multiple
                />
                <HcsCellSelector
                  className={styles.selectorContainer}
                  cells={selectedWell.images}
                  onChange={this.changeWellImages}
                  selected={selectedFields}
                  width={
                    HcsCellSelector.widthCorrection(selectedWell.width, selectedWell.images)
                  }
                  height={
                    HcsCellSelector.heightCorrection(selectedWell.height, selectedWell.images)
                  }
                  title={selectedWell.id}
                  cellShape={HcsCellSelector.Shapes.rect}
                  gridShape={HcsCellSelector.Shapes.circle}
                  gridRadius={selectedWell.radius}
                  flipVertical
                  showLegend={false}
                  multiple
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
                  multiple
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

export default roleModel.authenticationInfo(HcsImage);
