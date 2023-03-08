/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import moment from 'moment-timezone';
import {openReRunForm} from '../runs/actions';
import RunTable from '../runs/RunTable';
import compareArrays from '../../utils/compareArrays';
import PipelineRunFilter from '../../models/pipelines/PipelineRunFilter';
import styles from './Tools.css';

const PAGE_SIZE = 20;
const DEFAULT_RUN_STATUSES = [
  'SUCCESS',
  'FAILURE',
  'RUNNING',
  'STOPPED',
  'PAUSING',
  'PAUSED',
  'RESUMING'
];

@observer
export default class ToolHistory extends React.Component {
  runTable;

  @observable _runFilter;

  componentDidMount () {
    const {image} = this.props;
    if (image && !this.runFilter) {
      this.initializeRunFilter();
    }
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.image !== this.props.image) {
      if (this.runTable) {
        this.runTable.clearState();
      }
    }
  }

  componentDidUpdate () {
    const {image} = this.props;
    if (image && !this.runFilter) {
      this.initializeRunFilter();
    }
  }

  @computed
  get runFilter () {
    return this._runFilter;
  }

  initializeRunTable = (control) => {
    this.runTable = control;
  };

  initializeRunFilter = () => {
    const {image} = this.props;
    const filterParams = {
      page: 1,
      pageSize: PAGE_SIZE,
      dockerImages: [image],
      userModified: false,
      statuses: DEFAULT_RUN_STATUSES,
      owners: []
    };
    this._runFilter = new PipelineRunFilter(filterParams);
  };

  handleTableChange = (pagination, filter) => {
    const {image} = this.props;
    const {current, pageSize} = pagination;
    let modified = false;
    const statuses = filter.statuses && filter.statuses.length > 0
      ? filter.statuses
      : DEFAULT_RUN_STATUSES;
    if (statuses && !compareArrays(statuses, DEFAULT_RUN_STATUSES)) {
      modified = true;
    }
    const owners = filter.owners ? filter.owners : undefined;
    if (owners && owners.length > 0) {
      modified = true;
    }
    const startDateFrom = filter.started && filter.started.length === 1
      ? moment(filter.started[0]).utc(false).format('YYYY-MM-DD HH:mm:ss.SSS') : undefined;
    if (startDateFrom) {
      modified = true;
    }
    const endDateTo = filter.completed && filter.completed.length === 1
      ? moment(filter.completed[0]).utc(false).format('YYYY-MM-DD HH:mm:ss.SSS') : undefined;
    if (endDateTo) {
      modified = true;
    }
    const params = {
      page: current,
      pageSize,
      dockerImages: [image],
      statuses,
      owners,
      startDateFrom,
      endDateTo,
      userModified: modified
    };
    this.runFilter.filter(params);
  };

  reloadTable = () => {
    this.runFilter.fetch();
  };

  onSelectRun = ({id}) => {
    const {router} = this.props;
    router && router.push(`/run/${id}`);
  };

  launchPipeline = (run) => {
    return openReRunForm(run, this.props);
  };

  render () {
    const {image} = this.props;
    if (!this.runFilter || !image) {
      return null;
    }
    return (
      <RunTable
        onInitialized={this.initializeRunTable}
        useFilter
        className={styles.runTable}
        loading={!this.runFilter.loaded || this.runFilter.pending}
        dataSource={this.runFilter.value}
        pagination={{
          total: this.runFilter.total,
          pageSize: PAGE_SIZE
        }}
        hideColumns={['pipelineName']}
        reloadTable={this.reloadTable}
        launchPipeline={this.launchPipeline}
        onSelect={this.onSelectRun}
        dockerImagesDisabled
        handleTableChange={this.handleTableChange}
      />
    );
  }
}

ToolHistory.propTypes = {
  image: PropTypes.string.isRequired,
  router: PropTypes.object
};
