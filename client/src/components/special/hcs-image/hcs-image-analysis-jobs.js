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
import {Modal} from 'antd';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {CellProfilerJobs} from '../cellprofiler/components';
import CellProfilerJobResults from '../cellprofiler/components/cell-profiler-job-results';
import LoadingView from '../LoadingView';
import styles from './hcs-image.css';

@inject('authenticatedUserInfo')
@observer
class HcsImageAnalysisJobs extends React.Component {
  state = {
    jobId: undefined,
    filters: {},
    filtersInitialized: false
  };
  componentDidMount () {
    this.initializeFilters();
  }
  initializeFilters () {
    this.setState({
      filtersInitialized: false
    }, () => {
      const {
        authenticatedUserInfo
      } = this.props;
      authenticatedUserInfo
        .fetchIfNeededOrWait()
        .then(() => {
          const user = authenticatedUserInfo.loaded ? authenticatedUserInfo.value : undefined;
          if (user) {
            this.onChangeFilters({userNames: [user.userName]});
          } else {
            this.onChangeFilters({});
          }
        });
    });
  }
  onJobSelected = (jobId) => {
    this.setState({
      jobId
    });
  };
  onChangeFilters = (newFilters) => {
    this.setState({
      filters: newFilters,
      filtersInitialized: true
    });
  };
  render () {
    const {
      className,
      style
    } = this.props;
    const {
      filters,
      filtersInitialized,
      jobId
    } = this.state;
    if (!filtersInitialized) {
      return (<LoadingView />);
    }
    return (
      <div
        className={
          classNames(
            className,
            styles.hcsImageAnalysisJobs,
            'cp-panel',
            'cp-panel-borderless'
          )
        }
        style={style}
      >
        <CellProfilerJobs
          className={styles.hcsImageAnalysisJobsPanel}
          jobId={jobId}
          filters={filters}
          onJobSelected={this.onJobSelected}
          onFiltersChange={this.onChangeFilters}
          openFirst
        />
        <CellProfilerJobResults
          className={styles.hcsImageAnalysisJobsPanel}
          style={{flex: 1}}
          jobId={jobId}
        />
      </div>
    );
  }
}

HcsImageAnalysisJobs.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

function HcsImageAnalysisJobsModal (props) {
  const {
    visible,
    className,
    style,
    onClose
  } = props;
  return (
    <Modal
      width="calc(100vw - 50px)"
      style={{
        top: 20
      }}
      bodyStyle={{
        margin: 0,
        padding: 0,
        height: 'calc(100vh - 100px)'
      }}
      visible={visible}
      onCancel={onClose}
      maskClosable={false}
      title="HCS Images evaluations"
      footer={false}
    >
      {
        visible && (
          <HcsImageAnalysisJobs
            className={
              className
            }
            style={style}
          />
        )
      }
    </Modal>
  );
}

HcsImageAnalysisJobsModal.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  visible: PropTypes.bool,
  onClose: PropTypes.func
};

export {HcsImageAnalysisJobsModal};
export default HcsImageAnalysisJobs;
