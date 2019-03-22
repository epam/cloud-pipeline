/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {Component} from 'react';
import {inject, observer} from 'mobx-react';
import {Link} from 'react-router';
import {Row} from 'antd';
import pipelines from '../../../../models/pipelines/Pipelines';
import styles from './PipelineHistory.css';
import RunTable from '../../../runs/RunTable';
import connect from '../../../../utils/connect';
import pipelineRun from '../../../../models/pipelines/PipelineRun';
import parseQueryParameters from '../../../../utils/queryParameters';
import moment from 'moment';

const pageSize = 20;

@connect({
  pipelineRun, pipelines
})
@inject(({pipelineRun, routing}, {params}) => {
  const queryParameters = parseQueryParameters(routing);
  const allVersions = queryParameters.hasOwnProperty('all')
    ? (queryParameters.all === undefined ? true : queryParameters.all === 'true')
    : false;
  const filterParams = {
    page: 1,
    pageSize,
    pipelineIds: [params.id],
    userModified: false
  };

  if (!allVersions) {
    filterParams.versions = [params.version];
  }

  return {
    allVersions,
    runFilter: pipelineRun.runFilter(filterParams, true),
    pipeline: pipelines.getPipeline(params.id),
    pipelineId: params.id,
    version: params.version,
    routing
  };
})
@observer
export default class PipelineHistory extends Component {

  handleTableChange (pagination, filter) {
    const {current, pageSize} = pagination;
    let modified = false;
    const statuses = filter.statuses ? filter.statuses : undefined;
    if (statuses && statuses.length > 0) {
      modified = true;
    }
    const dockerImages = filter.dockerImages ? filter.dockerImages : undefined;
    if (dockerImages && dockerImages.length > 0) {
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
    const parentId = filter.parentRunIds && filter.parentRunIds.length === 1
      ? filter.parentRunIds[0] : undefined;
    if (parentId) {
      modified = true;
    }
    const params = {
      page: current,
      pageSize,
      pipelineIds: [this.props.pipelineId],
      dockerImages,
      statuses,
      startDateFrom,
      endDateTo,
      parentId,
      userModified: modified
    };

    if (!this.props.allVersions) {
      params.versions = [this.props.version];
    }

    this.props.runFilter.filter(params, true);
  }

  launchPipeline = ({pipelineId, version, id, configName}) => {
    if (pipelineId && version && id) {
      this.props.router.push(`/launch/${pipelineId}/${version}/${configName || 'default'}/${id}`);
    } else if (pipelineId && version && configName) {
      this.props.router.push(`/launch/${pipelineId}/${version}/${configName}`);
    } else if (pipelineId && version) {
      this.props.router.push(`/launch/${pipelineId}/${version}/default`);
    } else if (id) {
      this.props.router.push(`/launch/${id}`);
    }
  };

  onSelectRun = ({id}) => {
    this.props.router.push(`/run/${id}`);
  };

  reloadTable = () => {
    this.props.runFilter.fetch();
  };

  renderVersionsSwitch = () => {
    if (this.props.allVersions) {
      const currentVersionLink = `${this.props.pipelineId}/${this.props.version}/history`;
      return (
        <Row style={{marginBottom: 5, padding: 2}}>
          Currently viewing history for <b>all versions</b>. <Link to={currentVersionLink}>View only current version (<b>{this.props.version}</b>) history</Link>
        </Row>
      );
    } else {
      const allVersionsLink = `${this.props.pipelineId}/${this.props.version}/history?all`;
      return (
        <Row style={{marginBottom: 5, padding: 2}}>
          Currently viewing history for <b>{this.props.version}</b> version. <Link to={allVersionsLink}>View all versions history</Link>
        </Row>
      );
    }
  };

  render () {
    return (
      <div className={styles.container} style={{overflowY: 'auto'}}>
        {this.renderVersionsSwitch()}
        <RunTable
          onInitialized={this.initializeRunTable}
          useFilter={true}
          className={styles.runTable}
          loading={this.props.runFilter.pending}
          dataSource={this.props.runFilter.value}
          handleTableChange={::this.handleTableChange}
          versionsDisabled={true}
          pipelines={this.props.pipeline.pending ? [] : [this.props.pipeline.value]}
          pagination={{total: this.props.runFilter.total, pageSize}}
          reloadTable={this.reloadTable}
          launchPipeline={this.launchPipeline}
          onSelect={this.onSelectRun}
        />
      </div>
    );
  }

  initializeRunTable = (control) => {
    this.runTable = control;
  };

  componentWillReceiveProps (nextProps) {
    if (nextProps.allVersions !== this.props.allVersions) {
      if (this.runTable) {
        this.runTable.clearState();
      }
    }
  }
}
