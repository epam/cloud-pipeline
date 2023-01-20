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
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import {Icon, Tabs} from 'antd';
import CellProfiler, {CellProfilerJobs} from '../../cellprofiler/components';
import styles from './hcs-image-analysis.css';
import roleModel from '../../../../utils/roleModel';

const HcsAnalysisTabs = {
  analysis: 'analysis',
  batch: 'batch'
};

const HcsAnalysisTabName = {
  [HcsAnalysisTabs.analysis]: (<span><Icon type="play-circle" /> Analysis</span>),
  [HcsAnalysisTabs.batch]: (<span><Icon type="switcher" /> Evaluations</span>)
};

class HcsImageAnalysis extends React.Component {
  state = {
    activeTab: undefined,
    currentJobId: undefined,
    filters: undefined,
    userName: undefined
  };
  componentDidMount () {
    this.updateFiltersFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.source !== this.props.source) {
      this.updateFiltersFromProps();
    }
  }

  updateFiltersFromProps = () => {
    const {
      source
    } = this.props;
    const getUserNames = () => new Promise((resolve) => {
      const {authenticatedUserInfo} = this.props;
      if (authenticatedUserInfo) {
        authenticatedUserInfo
          .fetchIfNeededOrWait()
          .then(() => {
            if (authenticatedUserInfo.loaded) {
              const userName = authenticatedUserInfo.value.userName;
              resolve([userName]);
            } else {
              resolve([]);
            }
          })
          .catch(() => resolve([]));
      } else {
        resolve([]);
      }
    });
    getUserNames()
      .then((userNames) => {
        this.setState({
          filters: {
            userNames,
            source
          }
        });
      });
  };

  get activeTab () {
    const {
      batchMode,
      toggleBatchMode
    } = this.props;
    if (batchMode !== undefined && typeof toggleBatchMode === 'function') {
      // controlled
      return batchMode ? HcsAnalysisTabs.batch : HcsAnalysisTabs.analysis;
    }
    const {
      activeTab
    } = this.state;
    return activeTab || HcsAnalysisTabs.analysis;
  }
  get currentJobId () {
    const {
      batchJobId,
      toggleBatchMode
    } = this.props;
    if (typeof toggleBatchMode === 'function') {
      // controlled
      return batchJobId;
    }
    const {
      currentJobId
    } = this.state;
    return currentJobId;
  }
  onChangeTab = (key, batchJobId) => {
    const {
      batchMode,
      toggleBatchMode
    } = this.props;
    if (batchMode !== undefined && typeof toggleBatchMode === 'function') {
      toggleBatchMode(key === HcsAnalysisTabs.batch, batchJobId);
    } else {
      this.setState({activeTab: key, currentJobId: batchJobId});
    }
  };
  openAnalysis = () => {
    this.onChangeTab(HcsAnalysisTabs.analysis);
  };
  openEvaluations = (jobId) => {
    this.onChangeTab(HcsAnalysisTabs.batch, jobId);
  };
  onChangeFilters = (newFilters) => {
    this.setState({filters: newFilters});
  };
  renderAnalysis () {
    const {
      hcsAnalysis,
      expandSingle,
      onToggleResults,
      resultsVisible
    } = this.props;
    return (
      <CellProfiler
        className={styles.content}
        analysis={hcsAnalysis}
        expandSingle={expandSingle}
        onToggleResults={onToggleResults}
        resultsVisible={resultsVisible}
        onOpenEvaluations={this.openEvaluations}
      />
    );
  }
  renderEvaluations = () => {
    return (
      <CellProfilerJobs
        className={styles.content}
        jobId={this.currentJobId}
        onJobSelected={this.openEvaluations}
        filters={this.state.filters}
        onFiltersChange={this.onChangeFilters}
      />
    );
  };
  renderContent () {
    switch (this.activeTab) {
      case HcsAnalysisTabs.batch:
        return this.renderEvaluations();
      case HcsAnalysisTabs.analysis:
      default:
        return this.renderAnalysis();
    }
  }
  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <div
        className={
          classNames(
            styles.container,
            className,
            'cp-panel',
            'cp-panel-borderless'
          )
        }
        style={style}
      >
        <Tabs
          className="cp-tabs-no-padding"
          size="small"
          activeKey={this.activeTab}
          onChange={key => this.onChangeTab(key)}
        >
          <Tabs.TabPane
            key={HcsAnalysisTabs.analysis}
            tab={HcsAnalysisTabName[HcsAnalysisTabs.analysis]}
          />
          <Tabs.TabPane
            key={HcsAnalysisTabs.batch}
            tab={HcsAnalysisTabName[HcsAnalysisTabs.batch]}
          />
        </Tabs>
        {this.renderContent()}
      </div>
    );
  }
}

HcsImageAnalysis.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  expandSingle: PropTypes.bool,
  onToggleResults: PropTypes.func,
  resultsVisible: PropTypes.bool,
  batchMode: PropTypes.bool,
  batchJobId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toggleBatchMode: PropTypes.func,
  source: PropTypes.string
};

export default inject('hcsAnalysis')(
  roleModel.authenticationInfo(
    observer(HcsImageAnalysis)
  )
);
