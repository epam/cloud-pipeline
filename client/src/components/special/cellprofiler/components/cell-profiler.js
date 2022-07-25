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
import {Alert, Button, Checkbox, Icon, message} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import OpenPipelineModal from './modals/open-pipeline-modal';
import CellProfilerPipeline from './pipeline';
import AddModulesButton from './add-modules-button';
import SavePipelineModal from './modals/save-pipeline-modal';
import SelectionInfo from './selection-info';
import styles from './cell-profiler.css';

class CellProfiler extends React.Component {
  state = {
    managementActionsVisible: false,
    openPipelineModalVisible: false,
    savePipelineOptions: undefined
  };

  onSavePipelineClicked = async (asNew = false) => {
    const {
      analysis
    } = this.props;
    if (!analysis) {
      return;
    }
    if (asNew || !analysis.pipeline.name) {
      this.openSavePipelineModal(asNew);
    } else {
      return this.savePipeline(asNew);
    }
  };

  savePipeline = async (asNew) => {
    const {
      analysis
    } = this.props;
    if (!analysis) {
      return;
    }
    const hide = message.loading('Saving pipeline...', 0);
    try {
      await analysis.savePipeline(asNew);
    } catch (error) {
      message.error(error.message, 5);
    } finally {
      hide();
    }
  };

  openSavePipelineModal = (asNew = false) => {
    this.setState({
      savePipelineOptions: {asNew}
    });
  };

  closeSavePipelineModal = () => {
    this.setState({
      savePipelineOptions: undefined
    });
  };

  onNameSpecified = () => {
    const {savePipelineOptions} = this.state;
    const {
      asNew = false
    } = savePipelineOptions || {};
    this.closeSavePipelineModal();
    return this.savePipeline(asNew);
  };

  renderTitle = () => {
    const {
      analysis
    } = this.props;
    if (!analysis) {
      return null;
    }
    const disabled = false;
    const {
      openPipelineModalVisible,
      savePipelineOptions
    } = this.state;
    const openModal = () => {
      this.setState({openPipelineModalVisible: true});
    };
    const closeModal = () => {
      this.setState({openPipelineModalVisible: false});
    };
    const onPipelineSelected = (pipelineFile) => {
      closeModal();
      if (analysis) {
        (analysis.loadPipeline)(pipelineFile);
      }
    };
    const handleVisibility = (visible) => this.setState({managementActionsVisible: visible});
    const onSelect = ({key}) => {
      switch (key) {
        case 'New':
          analysis.newPipeline();
          break;
        case 'Save':
          (this.onSavePipelineClicked)();
          break;
        case 'SaveAsNew':
          (this.onSavePipelineClicked)(true);
          break;
        case 'Open':
          openModal();
          break;
      }
      handleVisibility(false);
    };
    const menu = (
      <div>
        <Menu
          selectedKeys={[]}
          onClick={onSelect}
        >
          <MenuItem key="New" disabled={disabled}>
            <Icon type="file" /> New
          </MenuItem>
          <MenuItem key="Open" disabled={disabled}>
            <Icon type="folder-open" /> Open
          </MenuItem>
          <MenuItem key="Save" disabled={disabled}>
            <Icon type="download" /> Save
          </MenuItem>
          <MenuItem key="SaveAsNew" disabled={disabled}>
            <Icon type="download" /> Save as new
          </MenuItem>
        </Menu>
      </div>
    );
    return (
      <Dropdown
        overlay={menu}
        trigger={['click']}
        onVisibleChange={handleVisibility}
      >
        <Button
          size="small"
        >
          <Icon type="bars" />
          <span>Pipeline</span>
          <OpenPipelineModal
            visible={openPipelineModalVisible}
            onSelect={onPipelineSelected}
            onClose={closeModal}
          />
          <SavePipelineModal
            pipeline={analysis.pipeline}
            visible={!!savePipelineOptions}
            onClose={this.closeSavePipelineModal}
            onSave={this.onNameSpecified}
          />
        </Button>
      </Dropdown>
    );
  };

  runAnalysis = () => {
    const {
      analysis,
      onOpenEvaluations
    } = this.props;
    if (!analysis) {
      return;
    }
    if (analysis.batch) {
      analysis
        .runBatch()
        .then((batchAnalysis) => {
          if (batchAnalysis && typeof onOpenEvaluations === 'function') {
            onOpenEvaluations(batchAnalysis.id);
          }
        });
    } else {
      (analysis.run)();
    }
  };

  toggleShowResults = () => {
    const {
      analysis,
      onToggleResults
    } = this.props;
    if (!analysis) {
      return null;
    }
    if (typeof onToggleResults === 'function') {
      onToggleResults();
    }
  }

  render () {
    const {
      analysis,
      className,
      style,
      resultsVisible,
      onOpenEvaluations
    } = this.props;
    if (!analysis) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            className,
            styles.cellProfiler
          )
        }
        style={style}
      >
        <div className={styles.cellProfilerHeader}>
          <span className={styles.title}>
            {this.renderTitle()}
          </span>
          <Button
            type="primary"
            style={{
              marginRight: 5
            }}
            size="small"
            disabled={
              !analysis.available ||
              analysis.analysing ||
              (
                analysis.batch &&
                analysis.pipeline &&
                analysis.pipeline.defineResultsAreEmpty
              )
            }
            onClick={this.runAnalysis}
          >
            <Icon type="caret-right" />
            {
              analysis.pending && (
                <Icon type="loading" />
              )
            }
          </Button>
          <AddModulesButton analysis={analysis} />
        </div>
        {
          analysis.batch &&
          analysis.pipeline &&
          analysis.pipeline.defineResultsAreEmpty && (
            <Alert
              className={styles.block}
              message={(
                <div>
                  To run evaluation please specify output at the <b>Define Results</b> section
                </div>
              )}
              type="warning"
              showIcon
            />
          )
        }
        <SelectionInfo
          analysis={analysis}
          showOnlyIfMultipleSelection
          showAsAlert
          onOpenEvaluations={onOpenEvaluations}
        />
        <CellProfilerPipeline
          className={styles.cellProfilerModules}
          pipeline={analysis.pipeline}
          viewer={analysis.hcsImageViewer}
        />
        {
          (analysis.analysisOutput) && (
            <div
              className={
                classNames(
                  styles.cellProfilerFooter
                )
              }
            >
              <Checkbox
                checked={resultsVisible}
                onChange={this.toggleShowResults}
              >
                Show results
              </Checkbox>
            </div>
          )
        }
        {
          (analysis.status || analysis.error) && (
            <div
              className={
                classNames(
                  styles.cellProfilerFooter,
                  {
                    'cp-text-not-important': !analysis.error,
                    'cp-error': !!analysis.error
                  }
                )
              }
            >
              {analysis.error || analysis.status}
            </div>
          )
        }
      </div>
    );
  }
}

CellProfiler.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  analysis: PropTypes.object,
  expandSingle: PropTypes.bool,
  onToggleResults: PropTypes.func,
  resultsVisible: PropTypes.bool,
  onOpenEvaluations: PropTypes.func
};

export default observer(CellProfiler);
