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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import StatusIcon from '../../../../special/StatusIcon';
import parseRunServiceUrl from '../../../../../utils/parseRunServiceUrl';
import {Icon, Popover, Table} from 'antd';
import styles from './SimpleRunsTable.css';
import moment from 'moment';

@observer
export default class SimpleRunsTable extends React.Component {

  static propTypes = {
    onRunClicked: PropTypes.func,
    runs: PropTypes.array
  };

  runTitleColumn = {
    dataIndex: 'podId',
    key: 'podId',
    className: styles.titleColumn,
    render: (podId, run) => {
      if (run.serviceUrl && run.initialized) {
        const urls = parseRunServiceUrl(run.serviceUrl);
        return (
          <Popover
            mouseEnterDelay={1}
            content={
              <div>
                <ul>
                  {
                    urls.map((url, index) =>
                      <li key={index} style={{margin: 4}}>
                        <a href={url.url} target="_blank">{url.name || url.url}</a>
                      </li>
                    )
                  }
                </ul>
              </div>
            }
            trigger="hover">
            <StatusIcon run={run} small /> <Icon type="export" /> {podId}
          </Popover>
        );
      } else {
        return (<span><StatusIcon run={run} small /> {podId}</span>);
      }
    }
  };

  pipelineColumn = {
    dataIndex: 'pipelineName',
    key: 'pipeline',
    className: styles.pipelineColumn,
    render: (name, run) => {
      if (name) {
        if (run.version) {
          return `${name} (${run.version})`;
        } else {
          return name;
        }
      } else if (run.dockerImage) {
        const parts = run.dockerImage.split('/');
        return parts[parts.length - 1];
      }
      return undefined;
    }
  };

  runningTimeColumn = {
    dataIndex: 'startDate',
    key: 'runningFor',
    className: styles.completedColumn,
    render: (started) => {
      if (started) {
        return moment.utc(started).fromNow(false);
      }
      return null;
    }
  };

  navigateToRun = ({id}) => {
    this.props.onRunClicked && this.props.onRunClicked(id);
  };

  render () {
    return (
      <Table
        key="table"
        showHeader={false}
        className={styles.table}
        columns={[this.runTitleColumn, this.pipelineColumn, this.runningTimeColumn]}
        dataSource={this.props.runs || []}
        rowKey="id"
        rowClassName={() => styles.tableRow}
        pagination={false}
        onRowClick={this.navigateToRun}
        size="medium" />
    );
  }
}
