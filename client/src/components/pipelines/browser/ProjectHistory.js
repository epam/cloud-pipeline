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

import React from 'react';
import {inject, observer} from 'mobx-react';
import folders from '../../../models/folders/Folders';
import pipelines from '../../../models/pipelines/Pipelines';
import RunTable from '../../runs/RunTable';
import LoadingView from '../../special/LoadingView';
import EditableField from '../../special/EditableField';
import {Alert, Icon, Row} from 'antd';
import connect from '../../../utils/connect';
import pipelineRun from '../../../models/pipelines/PipelineRun';
import moment from 'moment-timezone';
import styles from './Browser.css';

const PAGE_SIZE = 20;

@connect({
  folders,
  pipelines
})
@inject(({folders}, {params}) => {
  const filterParams = {
    page: 1,
    pageSize: PAGE_SIZE,
    projectIds: [params.id],
    userModified: false
  };
  return {
    runFilter: pipelineRun.runFilter(filterParams, true),
    folder: folders.load(params.id),
    folderId: params.id,
    id: params.id,
    folders,
    pipelines
  };
})
@observer
export default class ProjectHistory extends React.Component {

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
  initializeRunTable = (control) => {
    this.runTable = control;
  };

  handleTableChange (pagination, filter) {
    const {current, pageSize} = pagination;
    let modified = false;
    const statuses = filter.statuses ? filter.statuses : undefined;
    if (statuses && statuses.length) {
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
      projectIds: [this.props.folderId],
      dockerImages,
      statuses,
      startDateFrom,
      endDateTo,
      parentId,
      userModified: modified
    };
    this.props.runFilter.filter(params, true);
  }

  render () {
    if (this.props.folder.pending && !this.props.folder.loaded) {
      return <LoadingView />;
    }
    if (!this.props.folder.pending && this.props.folder.error) {
      return <Alert message={this.props.folder.error} type="error" />;
    }
    const folderTitleClassName = this.props.folder.value.locked ? styles.readonly : undefined;
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <table style={{width: '100%'}}>
          <tbody>
            <tr>
              <td>
                <Row type="flex" className={styles.itemHeader} align="middle">
                  <Icon type="clock-circle-o" className={`${styles.editableControl} ${folderTitleClassName}`} />
                  {
                    this.props.folder.value.locked &&
                    <Icon
                      className={`${styles.editableControl} ${folderTitleClassName}`}
                      type="lock" />
                  }
                  <EditableField
                    readOnly={true}
                    className={folderTitleClassName}
                    allowEpmty={false}
                    editStyle={{flex: 1}}
                    text={`${this.props.folder.value.name} runs history`} />
                </Row>
              </td>
              <td className={styles.currentFolderActions}>
                {'\u00A0'}
              </td>
            </tr>
          </tbody>
        </table>
        <RunTable
          onInitialized={this.initializeRunTable}
          useFilter={true}
          className={styles.runTable}
          loading={this.props.runFilter.pending}
          dataSource={this.props.runFilter.value}
          handleTableChange={::this.handleTableChange}
          pipelines={this.props.pipelines.pending ? [] : (this.props.pipelines.value || []).map(p => p)}
          pagination={{total: this.props.runFilter.total, pageSize: PAGE_SIZE}}
          reloadTable={this.reloadTable}
          launchPipeline={this.launchPipeline}
          onSelect={this.onSelectRun}
        />
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.id !== this.props.id) {
      if (this.runTable) {
        this.runTable.clearState();
      }
    }
  }
}
