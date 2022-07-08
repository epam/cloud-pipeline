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
import {Button, Checkbox, Icon, message} from 'antd';
import Menu, {MenuItem, SubMenu} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import {allModules} from '../model/modules';
import OpenPipelineModal from './open-pipeline-modal';
import CellProfilerPipeline from './pipeline';
import styles from './cell-profiler.css';

class CellProfiler extends React.Component {
  state = {
    addModuleSelectorVisible: false,
    managementActionsVisible: false,
    openPipelineModalVisible: false
  };

  renderAddModuleSelector = () => {
    const {analysis} = this.props;
    const handleVisibility = (visible) => this.setState({addModuleSelectorVisible: visible});
    const filtered = allModules.filter(module => !module.hidden);
    const onSelect = ({key}) => {
      const cpModule = filtered.find((cpModule) => cpModule.name === key);
      if (analysis) {
        analysis.add(cpModule);
      }
      handleVisibility(false);
    };
    const groups = [...(new Set(filtered.map(module => module.group)))].filter(Boolean);
    const mainModules = filtered.filter(module => !module.group);
    const menu = (
      <div>
        <Menu
          selectedKeys={[]}
          onClick={onSelect}
        >
          {
            mainModules
              .map((cpModule) => (
                <MenuItem key={cpModule.name}>
                  {cpModule.title || cpModule.name}
                </MenuItem>
              ))
          }
          {
            groups.map((group) => (
              <SubMenu
                key={group}
                title={group}
                selectedKeys={[]}
              >
                {
                  filtered
                    .filter(module => module.group === group)
                    .map((cpModule) => (
                      <MenuItem key={cpModule.name}>
                        {cpModule.title || cpModule.name}
                      </MenuItem>
                    ))
                }
              </SubMenu>
            ))
          }
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
          disabled={!analysis.ready || analysis.pending || analysis.analysing}
        >
          <Icon type="plus" />
          <span>Add module</span>
        </Button>
      </Dropdown>
    );
  };

  renderTitle = () => {
    const {analysis} = this.props;
    if (!analysis) {
      return null;
    }
    const {
      openPipelineModalVisible
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
          (async () => {
            const hide = message.loading('Saving pipeline...', 0);
            try {
              await analysis.savePipeline();
            } catch (error) {
              message.error(error.message, 5);
            } finally {
              hide();
            }
          })();
          break;
        case 'SaveAsNew':
          (async () => {
            const hide = message.loading('Saving pipeline...', 0);
            try {
              await analysis.savePipeline(true);
            } catch (error) {
              message.error(error.message, 5);
            } finally {
              hide();
            }
          })();
          break;
        case 'Open': openModal(); break;
      }
      handleVisibility(false);
    };
    const menu = (
      <div>
        <Menu
          selectedKeys={[]}
          onClick={onSelect}
        >
          <MenuItem key="New">
            <Icon type="file" /> New
          </MenuItem>
          <MenuItem key="Open">
            <Icon type="folder-open" /> Open
          </MenuItem>
          <MenuItem key="Save">
            <Icon type="download" /> Save
          </MenuItem>
          <MenuItem key="SaveAsNew">
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
        <a
          className="cp-text"
        >
          Analysis <Icon type="down" />
          <OpenPipelineModal
            visible={openPipelineModalVisible}
            onSelect={onPipelineSelected}
            onClose={closeModal}
          />
        </a>
      </Dropdown>
    );
  };

  runAnalysis = () => {
    const {
      analysis
    } = this.props;
    if (!analysis) {
      return;
    }
    analysis.run();
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
      resultsVisible
    } = this.props;
    if (!analysis) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            className,
            styles.cellProfiler,
            'cp-panel'
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
            disabled={!analysis.ready || analysis.analysing}
            onClick={this.runAnalysis}
          >
            <Icon type="caret-right" />
            {
              analysis.pending && (
                <Icon type="loading" />
              )
            }
          </Button>
          {this.renderAddModuleSelector()}
        </div>
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
  resultsVisible: PropTypes.bool
};

export default observer(CellProfiler);
