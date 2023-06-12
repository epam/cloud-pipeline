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
import FileSaver from 'file-saver';

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
import ObjectsOutline from '../cellprofiler/components/objects-outline';
import {cellsAreEqual, ascSorter} from './utilities/cells-utilities';
import CellProfilerJobResults from '../cellprofiler/components/cell-profiler-job-results';
import HcsVideoPlayer from './hcs-video-player';
import HcsVideoSource from './hcs-video-player/hcs-video-source';
import VideoButton from './hcs-video-player/video-button';
import styles from './hcs-image.css';

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
    batchJobId: undefined,
    selectedSequenceTimePoints: [],
    selectedZCoordinates: [],
    mergeZPlanes: false,
    selectedWells: [],
    selectedFields: []
  };

  @observable hcsInfo;
  @observable container;
  @observable hcsViewerState = new ViewerState();
  @observable hcsSourceState = new SourceState();
  @observable hcsVideoSource = new HcsVideoSource();
  @observable hcsImageViewer;
  @observable hcsAnalysis = new Analysis();

  componentDidMount () {
    this.hcsVideoSource.attachViewerState(this.hcsViewerState);
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
    this.hcsVideoSource.destroy();
    this.hcsVideoSource = undefined;
    this.hcsAnalysis.removeEventListeners(Analysis.Events.analysisDone, this.onAnalysisDone);
    this.hcsAnalysis.destroy();
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

  get showBatchJobInfo () {
    const {
      batchJobId,
      batchAnalysis
    } = this.state;
    return batchAnalysis && batchJobId;
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
      this.hcsVideoSource.setHcsFile();
    }
    if ((storageId || storage) && path) {
      if (this.hcsAnalysis) {
        this.hcsAnalysis.setSource(
          storage ? storage.id : storageId,
          path
        );
      }
      this.setState({
        sequencePending: false,
        pending: true,
        error: undefined,
        plateWidth: 0,
        plateHeight: 0,
        selectedSequenceTimePoints: [],
        selectedZCoordinates: [],
        mergeZPlanes: false
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
              this.hcsVideoSource.setHcsFile({
                storageId: storage ? storage.id : storageId,
                path,
                pathMask: info && info.objectStorage ? info.objectStorage.pathMask : undefined
              });
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
              selectedZCoordinates: [],
              mergeZPlanes: false
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
        selectedZCoordinates: [],
        mergeZPlanes: false
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
        this.loadImageForAnalysis();
      } else if (newSequenceId !== currentSequenceId) {
        this.changeSequence();
      } else if (this.hcsImageViewer) {
        this.hcsImageViewer.setGlobalTimePosition(timePointId);
        this.loadImageForAnalysis();
      }
      this.hcsVideoSource.setSequenceTimePoints(
        newSequenceId,
        timePointId,
        newSequence ? (newSequence.timeSeries || []).length > 1 : false
      );
    });
  };

  onChangeZCoordinates = (selection = [], mergeZPlanes = false) => {
    const currentZ = this.selectedZCoordinate;
    this.setState({
      selectedZCoordinates: selection.slice(),
      mergeZPlanes
    }, () => {
      const newZ = this.selectedZCoordinate;
      if (newZ !== currentZ && this.hcsImageViewer) {
        this.hcsImageViewer.setGlobalZPosition(Number(newZ));
      }
      this.hcsVideoSource.setZPlanes(this.selectedZCoordinate);
      this.loadImageForAnalysis();
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
              } else {
                this.loadImageForAnalysis();
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
      } else {
        this.loadImageForAnalysis();
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
      const multipleZCoordinates = fields[0].depth > 1;
      if (this.showEntireWell) {
        url = well.overviewOmeTiffFileName;
        offsetsJsonUrl = well.overviewOffsetsJsonFileName;
        id = well.wellImageId;
      }
      if (this.hcsVideoSource) {
        this.hcsVideoSource.setWellView(
          this.showEntireWell,
          id,
          well,
          multipleZCoordinates
        );
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
              this.loadImageForAnalysis();
            }
          });
      }
    }
  };

  onAnalysisDone = () => {
    const resultsAvailable = !!this.hcsAnalysis &&
      !!this.hcsAnalysis.defineResultsOutputs &&
      this.hcsAnalysis.defineResultsOutputs.length > 0;
    this.setState({
      showAnalysisResults: resultsAvailable
    });
  };

  loadImageForAnalysis = () => {
    if (this.hcsInfo && this.hcsAnalysis && this.hcsImageViewer) {
      const {
        selectedWells,
        selectedFields,
        selectedSequenceTimePoints,
        selectedZCoordinates: zCoordinates = [],
        mergeZPlanes
      } = this.state;
      const selectedZCoordinates = zCoordinates.length > 0 ? zCoordinates : [0];
      const analysisInputs = [];
      const imageSelected = anImage => selectedFields
        .some(aField => aField.fieldID === anImage.fieldID);
      this.selectedSequences.forEach(sequence => {
        const timePoints = selectedSequenceTimePoints
          .filter(o => o.sequence === sequence.id)
          .map(o => Number(o.timePoint));
        const wells = sequence.wells
          .filter(aWell => selectedWells.some(w => w.id === aWell.id));
        wells.forEach(aWell => {
          const {
            x: row,
            y: column
          } = aWell;
          let images = aWell.images.filter(imageSelected);
          if (images.length === 0) {
            // we should select all fields from the well
            images = aWell.images.slice();
          }
          images.forEach(anImage => {
            timePoints.forEach(aTimePoint => {
              selectedZCoordinates.forEach(z => {
                (anImage.channels || []).forEach((channel, channelIndex) => {
                  analysisInputs.push({
                    sourceDirectory: sequence.sourceDirectory,
                    x: column + 1,
                    y: row + 1,
                    z: Number(z) + 1,
                    t: Number(aTimePoint) + 1,
                    fieldID: anImage.fieldID,
                    c: channelIndex + 1,
                    channel: channel
                  });
                });
              });
            });
          });
        });
      });
      const sequence = this.selectedSequence;
      const fields = this.selectedWellFields;
      if (
        sequence &&
        sequence.sourceDirectory &&
        fields.length > 0
      ) {
        this.hcsAnalysis.updatePhysicalSize(
          fields[0].physicalSize,
          fields[0].unit
        );
        this.hcsAnalysis.changeFile(analysisInputs, mergeZPlanes);
        this.hcsAnalysis.hcsImageViewer = this.hcsImageViewer;
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

  toggleBatchAnalysis = (batchAnalysis, jobId) => {
    this.setState({
      batchAnalysis,
      batchJobId: batchAnalysis ? jobId : undefined
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

  renderDownloadBtn (options = {}) {
    const {
      showTitle = false,
      buttonSize = 'small'
    } = options;
    const {
      videoMode,
      videoUrl,
      videoAccessCallback = () => {},
      videoPending
    } = this.hcsVideoSource;
    const downloadAvailable = videoMode
      ? (videoUrl && !videoPending)
      : downloadCurrentTiffAvailable(this.hcsImageViewer);
    const handleClickDownload = () => {
      if (videoMode) {
        videoAccessCallback();
        fetch(videoUrl)
          .then(res => res.blob())
          .then(blob => FileSaver.saveAs(blob, this.hcsVideoSource.getVideoFileName(videoUrl)));
      } else {
        downloadCurrentTiff(
          this.hcsImageViewer,
          {
            wellView: this.showEntireWell,
            wellId: this.selectedWell ? this.selectedWell.id : undefined
          }
        );
      }
    };
    const btnContent = showTitle
      ? `Download current ${videoMode ? 'video' : 'image'}`
      : (
        <Icon
          type={videoMode ? 'download' : 'camera'}
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
    const analysisAvailable = this.hcsAnalysis && this.hcsAnalysis.available;
    const selectedImage = this.selectedWellFields[0];
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
          <VideoButton
            className={styles.action}
            videoSource={this.hcsVideoSource}
            available={
              (this.selectedSequence && this.selectedSequence.timeSeries.length > 1) ||
              (selectedImage && selectedImage.depth > 1)
            }
          />
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
          {
            analysisAvailable && (
              <Button
                className={styles.action}
                size="small"
                onClick={this.toggleAnalysis}
                type={showAnalysis ? 'primary' : 'default'}
                disabled={this.hcsVideoSource.videoMode}
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
          {this.renderDownloadBtn({showTitle: true, buttonSize: 'default'})}
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
      this.hcsAnalysis.defineResultsOutputs &&
      this.hcsAnalysis.defineResultsOutputs.length > 0
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

  renderHcsImageAnalysis = () => {
    const {
      showAnalysis,
      showAnalysisResults,
      batchAnalysis,
      batchJobId
    } = this.state;
    if (showAnalysis) {
      const {
        path
      } = this.props;
      const sourceName = (path || '').split(/[\\/]/).pop();
      return (
        <HcsImageAnalysis
          className={styles.analysis}
          onToggleResults={this.toggleAnalysisResults}
          resultsVisible={showAnalysisResults}
          batchMode={batchAnalysis}
          batchJobId={batchJobId}
          toggleBatchMode={this.toggleBatchAnalysis}
          source={sourceName}
        />
      );
    }
    return null;
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
      (
        !this.hcsImageViewer &&
        !this.hcsVideoSource.videoMode
      ) ||
      (
        this.hcsVideoSource.videoMode &&
        this.hcsVideoSource.videoPending
      ) ||
      this.hcsSourceState.pending ||
      this.hcsViewerState.pending;
    if (this.showBatchJobInfo) {
      return null;
    }
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
                [styles.pending]: pending,
                [styles.hidden]: this.hcsVideoSource.videoMode
              }
            )
          }
          ref={this.init}
        >
          {'\u00A0'}
        </div>
        {
          this.hcsVideoSource.videoMode && (
            <HcsVideoPlayer
              videoSource={this.hcsVideoSource}
              className={styles.hcsImage}
            />
          )
        }
        {this.renderHcsAnalysisResults()}
      </div>
    );
  }

  renderSelectors () {
    const {
      plateWidth,
      plateHeight,
      selectedSequenceTimePoints = [],
      selectedZCoordinates = [],
      mergeZPlanes,
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
      selectedWell &&
      !this.showBatchJobInfo
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
            style={{padding: 5}}
          />
          <HcsZPositionSelector
            image={selectedImage}
            selection={selectedZCoordinates}
            mergeZPlanes={mergeZPlanes}
            onChange={this.onChangeZCoordinates}
            multiple
          />
        </div>
      );
    }
    return null;
  }

  renderBatchInfo = () => {
    if (!this.showBatchJobInfo) {
      return null;
    }
    const {
      batchJobId
    } = this.state;
    return (
      <CellProfilerJobResults
        className={styles.hcsBatchJobInfo}
        jobId={batchJobId}
      />
    );
  };

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
        hcsVideoSource={this.hcsVideoSource}
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
          {this.renderHcsImageAnalysis()}
          {this.renderViewer()}
          {this.renderSelectors()}
          {this.renderBatchInfo()}
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
