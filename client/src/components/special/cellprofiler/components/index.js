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
import {Button, Icon} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import {observer} from 'mobx-react';
import CellProfilerModule from './module';
import {allModules} from '../model/modules';
import styles from './cell-profiler.css';
import classNames from 'classnames';

class CellProfiler extends React.Component {
  state = {
    addModuleSelectorVisible: false,
    expanded: []
  };

  renderAddModuleSelector = () => {
    const {analysis} = this.props;
    const handleVisibility = (visible) => this.setState({addModuleSelectorVisible: visible});
    const onSelect = ({key}) => {
      const cpModule = allModules.find((cpModule) => cpModule.identifier === key);
      if (analysis) {
        analysis.add(cpModule)
          .then((newModule) => {
            this.toggleExpanded(newModule);
          });
      }
      handleVisibility(false);
    };
    console.log(allModules);
    const menu = (
      <div>
        <Menu
          selectedKeys={[]}
          onClick={onSelect}
        >
          {
            allModules
              .filter((cpModule) => !cpModule.predefined)
              .map((cpModule) => (
                <MenuItem key={cpModule.identifier}>
                  {cpModule.moduleTitle}
                </MenuItem>
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

  getModuleExpanded = (cpModule) => {
    const {expanded = []} = this.state;
    return expanded.includes(cpModule.id);
  }

  toggleExpanded = (cpModule) => {
    const {expandSingle} = this.props;
    const {expanded = []} = this.state;
    if (expanded.includes(cpModule.id)) {
      this.setState({expanded: expanded.filter(o => o !== cpModule.id)});
    } else if (expandSingle) {
      this.setState({expanded: [cpModule.id]});
    } else {
      this.setState({expanded: [...expanded, cpModule.id]});
    }
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

  render () {
    const {
      analysis,
      className,
      style
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
            Analysis
            {
              analysis.pending && (
                <Icon type="loading" />
              )
            }
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
          </Button>
          {this.renderAddModuleSelector()}
        </div>
        <div
          className={styles.cellProfilerModules}
        >
          <CellProfilerModule
            cpModule={analysis.namesAndTypes}
            expanded={this.getModuleExpanded(analysis.namesAndTypes)}
            onExpandedChange={() => this.toggleExpanded(analysis.namesAndTypes)}
            movable={false}
            removable={false}
          />
          {
            (analysis.modules || []).filter(cpModule => !cpModule.hidden).map((cpModule) => (
              <CellProfilerModule
                key={cpModule.displayName}
                cpModule={cpModule}
                expanded={this.getModuleExpanded(cpModule)}
                onExpandedChange={() => this.toggleExpanded(cpModule)}
              />
            ))
          }
        </div>
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
  expandSingle: PropTypes.bool
};

export default observer(CellProfiler);
