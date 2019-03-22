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
import {Table} from 'antd';
import PipelineRunFilter from '../../models/pipelines/PipelineRunFilter';
import styles from './RunsSearch.css';
import StatusIcon from '../special/StatusIcon';
import localization from '../../utils/localization';

const pageSize = 20;

@inject(({routing}) => {
  const queryParameters = routing.location.search &&
    routing.location.search.substring(1, routing.location.search.length)
      .split('&').map(s => {
      const parts = s.split('=');
      let value = parts[1];
      for (let i = 2; i < parts.length; i++) {
        value += `=${parts[i]}`;
      }
      return {
        key: parts[0],
        value: value
      }
    }).reduce((obj, current) => {
      obj[current.key] = current.value;
      return obj;
    }, {});
  const runParams = {
    page: 1,
    pageSize: pageSize,
    partialParameters: queryParameters.text
  };
  return {
    text: queryParameters.text,
    search: queryParameters.text,
    runFilter: new PipelineRunFilter(runParams)
  };
})
@localization.localizedComponent
@observer
export default class RunsSearch extends localization.LocalizedReactComponent {

  state = {currentPage: 1};

  parametersRenderer = (text) => {
    let index = 0;
    const parts = [];
    let found = true;
    const originalText = text;
    while (found) {
      text = text.substring(index);
      const textStart = text.toLowerCase().indexOf(this.props.search.toLowerCase());
      if (textStart >= 0) {
        const textEnd = textStart + this.props.search.length;
        index = textEnd + 1;
        parts.push({start: textStart, end: textEnd});
      } else {
        found = false;
      }
    }
    const maxLength = 40;
    const textStart = (parts.length ? parts[0].start : 0);
    const textEnd = (parts.length ? parts[0].end : 0);
    const start = Math.max(0, textStart - maxLength / 2);
    const end = Math.min(textEnd + maxLength / 2, originalText.length - 1);
    const before = originalText.substring(start, textStart);
    const highlighted = originalText.substring(textStart, textEnd);
    const after = originalText.substring(textEnd, end);
    return (
      <div>
        ...
        <span className={styles.parametersTextDefault}>{before}</span>
        <span className={styles.parametersTextHighlighted}>{highlighted}</span>
        <span className={styles.parametersTextDefault}>{after}</span>
        ...
      </div>
    );
  };

  getFilterParams () {
    return {
      page: this.state.currentPage,
      pageSize,
      partialParameters: this.props.text
    };
  }

  handleTableChange = (pagination) => {
    const {current} = pagination;
    this.setState({
      currentPage: current
    });
  };

  get columns () {
    return [
      {
        key: 'icon',
        dataIndex: 'status',
        render: (val, run) => (<StatusIcon run={run} small />)
      },
      {
        dataIndex: 'id',
        key: 'id',
        title: 'Run'
      },
      {
        dataIndex: 'pipelineName',
        key: 'pipelineName',
        title: this.localizedString('Pipeline')
      },
      {
        title: 'Version',
        dataIndex: 'version',
        key: 'version'
      },
      {
        title: 'Started',
        dataIndex: 'startDate',
        key: 'startDate'
      },
      {
        title: 'Completed',
        dataIndex: 'endDate',
        key: 'endDate'
      },
      {
        title: 'Owner',
        dataIndex: 'owner',
        key: 'owner'
      },
      {
        dataIndex: 'params',
        key: 'params',
        title: 'Parameters',
        render: (params) => this.parametersRenderer(params)
      }
    ];
  }

  render () {
    const items = this.props.runFilter.value ? this.props.runFilter.value.map(r => r) : undefined;
    return (
      <Table
        columns={this.columns}
        loading={this.props.runFilter.pending}
        dataSource={items}
        onChange={this.handleTableChange}
        pagination={{total: this.props.runFilter.total, pageSize, current: this.state.currentPage}}
        rowKey="id"
        onRowClick={run => this.goToRun(run)}
        rowClassName={() => styles.row}
        className={styles.runsSearchTable}
        size="small" />
    );
  }

  goToRun = (run) => {
    this.props.router.push(`/run/${run.id}`);
  };

  componentDidUpdate(prevProps, prevState) {
    if (prevState.currentPage !== this.state.currentPage) {
      this.setState({currentPage: this.state.currentPage});
      this.props.runFilter.filter(this.getFilterParams());
    }
  }
}
