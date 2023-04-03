/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer, inject} from 'mobx-react';
import {computed, observable} from 'mobx';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import moment from 'moment-timezone';
import FileSaver from 'file-saver';
import {Link} from 'react-router';
import {
  Button,
  message,
  Icon,
  Table
} from 'antd';
import {
  Period,
  getPeriod,
  getCurrentDate
} from '../../../../special/periods';
import displayDate from '../../../../../utils/displayDate';
import {
  GetGroupedTools,
  GetGroupedPipelines,
  GetGroupedStorages
} from '../../../../../models/billing';
import exportBillingURL from '../../../../../models/billing/export';
import UserCostsPanel from '../../../../../components/main/home/panels/UserCostsPanel';
import {
  COLUMNS,
  TABLE_CATEGORIES,
  TABLE_MODES,
  GROUPING
} from './columns';
import styles from './UserInfoSummary.css';

const TOP_RUNS_AMOUNT = 3;

@inject('dockerRegistries')
@observer
export default class UserInfoSummary extends React.Component {
  state={
    pending: false,
    period: moment(getCurrentDate()),
    tableMode: TABLE_MODES.computed
  }

  @observable
  requests

  componentDidMount = () => {
    const {dockerRegistries} = this.props;
    this.loadData();
    dockerRegistries.fetchIfNeededOrWait();
  }

  componentDidUpdate = (prevProps) => {
    const {user} = this.props;
    if (user && (prevProps.user || {}).userName !== user.userName) {
      this.loadData();
    }
  }

  @computed
  get statistics () {
    const runs = {
      pipelines: [],
      tools: []
    };
    if (
      this.requests &&
      this.requests.pipelines.loaded &&
      this.requests.tools.loaded
    ) {
      runs.pipelines = Object.values(this.requests.pipelines._value || {})
        .sort((a, b) => b.runsCount - a.runsCount)
        .slice(0, TOP_RUNS_AMOUNT);
      runs.tools = Object.values(this.requests.tools._value || {})
        .sort((a, b) => b.runsCount - a.runsCount)
        .slice(0, TOP_RUNS_AMOUNT);
    }
    return runs;
  }

  @computed
  get tableData () {
    const {tableMode} = this.state;
    if (
      this.requests &&
      this.requests.pipelines.loaded &&
      this.requests.tools.loaded &&
      this.requests.storages.loaded
    ) {
      return [
        this.requests.pipelines,
        this.requests.tools,
        this.requests.storages
      ].reduce((acc, billing) => {
        const billingGroup = Object
          .values(billing._value || {})
          .map(billingEntry => ({
            ...billingEntry,
            type: GROUPING[billing.grouping] || billing.grouping
          }));
        acc.push(...billingGroup);
        return acc;
      }, []).sort((a, b) => {
        if (tableMode === TABLE_MODES.computed) {
          return b.runsCount - a.runsCount;
        }
        return b.usage - a.usage;
      });
    }
    return [];
  }

  @computed
  get filteredTableData () {
    const {tableMode} = this.state;
    if (tableMode) {
      return this.tableData
        .filter(({type}) => TABLE_CATEGORIES[tableMode].includes(type));
    }
    return this.tableData;
  }

  @computed
  get tools () {
    const {dockerRegistries} = this.props;
    const result = [];
    if (dockerRegistries.loaded) {
      const {registries = []} = dockerRegistries.value || {};
      for (let r = 0; r < registries.length; r++) {
        const registry = registries[r];
        const {groups = []} = registry;
        for (let g = 0; g < groups.length; g++) {
          const group = groups[g];
          const {tools: groupTools = []} = group;
          for (let t = 0; t < groupTools.length; t++) {
            const tool = groupTools[t];
            if (!tool.link) {
              result.push({
                ...tool,
                dockerImage: `${registry.path}/${tool.image}`.toLowerCase(),
                registry,
                group,
                name: tool.image.split('/').pop()
              });
            }
          }
        }
      }
    }
    return result;
  }

  setTableMode = (mode) => {
    const {tableMode} = this.state;
    if (tableMode !== mode) {
      this.setState({tableMode: mode});
    }
  };

  loadData = () => {
    const hide = message.loading('Loading data...', 0);
    this.setState({pending: true}, async () => {
      const {user} = this.props;
      const {period} = this.state;
      const range = moment(period).format('YYYY-MM');
      const periodInfo = getPeriod(Period.month, range);
      const filters = {
        user: [user.userName],
        ...periodInfo
      };
      this.requests = {
        pipelines: new GetGroupedPipelines({filters}),
        tools: new GetGroupedTools({filters}),
        storages: new GetGroupedStorages({filters})
      };
      const promises = Object.values(this.requests)
        .map(request => new Promise(resolve => {
          request.fetch()
            .then(() => {
              resolve(request);
            })
            .catch(() => resolve(request));
        }));
      await Promise.all(promises);
      promises.forEach(request => {
        if (request.error) {
          message.error(request.error, 5);
        }
      });
      this.setState({pending: false});
      hide();
    });
  };

  getToolId = (docker) => {
    const [r, g, iv] = docker.split('/');
    const image = iv.split(':').slice(0, -1).join(':');
    const dockerImage = [r, g, image].join('/').toLowerCase();
    const currentTool = this.tools.find(tool => tool.dockerImage === dockerImage);
    if (currentTool) {
      return currentTool.id;
    }
    return undefined;
  };

  changePeriod = (shift) => () => {
    this.setState(prevState => ({
      period: moment(prevState.period).add(shift, 'M')
    }), () => {
      this.loadData();
    });
  };

  exportToCSV = () => {
    const {user} = this.props;
    const {period} = this.state;
    const range = moment(period).format('YYYY-MM');
    const periodInfo = getPeriod(Period.month, range);
    const filters = {
      owner: [user.userName]
    };
    const payload = {
      types: ['RUN'],
      from: periodInfo.start.format('YYYY-MM-DD'),
      to: periodInfo.endStrict.format('YYYY-MM-DD'),
      filters,
      discount: {computes: 0, storages: 0}
    };
    const fileName = `${user.userName}_${range}_statistics.csv`;
    const request = new XMLHttpRequest();
    request.withCredentials = true;
    request.onreadystatechange = function () {
      if (request.readyState !== 4) return;
      if (request.status !== 200) {
        const error = request.statusText
          ? `Export error: ${request.statusText}`
          : 'Export error';
        message.error(error, 5);
      } else {
        const response = request.responseText;
        try {
          const {status, message} = JSON.parse(response);
          if (status && !/^OK$/i.test(status)) {
            message.error(message, 5);
            return;
          }
        } catch (_) {}
        try {
          const blob = new Blob([response], {type: 'text/csv;charset=utf-8'});
          FileSaver.saveAs(blob, fileName);
        } catch (e) {
          message.error(e, 5);
        }
      }
    };
    request.open('POST', exportBillingURL());
    request.timeout = 1000 * 60 * 5; // 5 minutes;
    request.setRequestHeader('Content-Type', 'application/json');
    request.send(JSON.stringify(payload));
  };

  renderUserStatistics = () => {
    const {user} = this.props;
    const {period} = this.state;
    const getLink = (item, type) => {
      if (type === 'tool') {
        // eslint-disable-next-line no-unused-vars
        const [_docker, ...rest] = item.fullName.split('/');
        const name = rest.join('/');
        const toolId = this.getToolId(item.fullName);
        const url = `/tool/${toolId}`;
        return toolId
          ? <Link to={url}>{name}</Link>
          : name;
      }
      return item.fullName || item.name;
    };
    return (
      <div className={styles.statisticsContainer}>
        <p className={classNames(
          styles.statisticsBlock,
          'cp-divider',
          'right'
        )}>
          <b>Most used tools (top {TOP_RUNS_AMOUNT})</b>
          {this.statistics.tools.map((tool) => (
            <span
              key={`${tool.name}_${tool.runsCount}`}
              style={{marginLeft: 5}}
            >
              {getLink(tool, 'tool')}
            </span>
          ))}
        </p>
        <p className={classNames(
          styles.statisticsBlock,
          'cp-divider',
          'right'
        )}>
          <b>Most used pipelines (top {TOP_RUNS_AMOUNT})</b>
          {this.statistics.pipelines.map(pipeline => (
            <span
              key={`${pipeline.name}_${pipeline.runsCount}`}
              style={{marginLeft: 5}}
            >
              {getLink(pipeline, 'pipeline')}
            </span>
          ))}
        </p>
        <p className={classNames(
          styles.statisticsBlock,
          'cp-divider',
          'right'
        )}>
          <b>
            Last connection date
          </b>
          <span style={{marginLeft: 5}}>
            {displayDate(user.lastLoginDate, 'MMM D, YYYY, HH:mm')}
          </span>
        </p>
        <p
          className={
            classNames(
              styles.statisticsBlock,
              'cp-divider',
              'right'
            )
          }
          style={{minWidth: '200px'}}
        >
          <b>
            Usage costs
          </b>
          <UserCostsPanel
            showDisclaimer={false}
            userName={user.userName}
            billingPeriods={[Period.month]}
            period={period}
            showPeriodHeaders={false}
          />
        </p>
      </div>
    );
  };

  renderTable = () => {
    const {tableMode, pending} = this.state;
    return (
      <div className={styles.details}>
        <p className={styles.title}>
          Detailed usage data
        </p>
        <div className={styles.tableControls}>
          <Button.Group>
            <Button
              type={tableMode === TABLE_MODES.computed ? 'primary' : 'default'}
              onClick={() => this.setTableMode(TABLE_MODES.computed)}
              size="small"
            >
              Compute instances
            </Button>
            <Button
              type={tableMode === TABLE_MODES.storages ? 'primary' : 'default'}
              onClick={() => this.setTableMode(TABLE_MODES.storages)}
              size="small"
            >
              Storages
            </Button>
          </Button.Group>
        </div>
        <Table
          dataSource={this.filteredTableData}
          columns={COLUMNS[tableMode]}
          className={styles.resourcesTable}
          rowClassName={() => styles.resourcesTableRow}
          size="small"
          rowKey={(item) => `${item.name}_${item.type}`}
          loading={pending}
        />
      </div>
    );
  };

  renderTitle = () => {
    const {period, pending} = this.state;
    const currentPeriod = displayDate(period, 'MMMM YYYY');
    const nexNavigationDisabled = moment(period).add(1, 'M')
      .isSameOrAfter(moment(getCurrentDate()));
    return (
      <div className={styles.title}>
        <Button
          onClick={this.changePeriod(-1)}
          size="small"
          style={{marginRight: 10}}
          disabled={pending}
        >
          <Icon type="left" />
        </Button>
        <span>
          {`User statistics for ${currentPeriod}`}
        </span>
        <Button
          onClick={this.changePeriod(1)}
          size="small"
          style={{marginLeft: 10}}
          disabled={pending || nexNavigationDisabled}
        >
          <Icon type="right" />
        </Button>
      </div>
    );
  };

  render () {
    const {
      user,
      className,
      style
    } = this.props;
    if (!user) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.container,
            className
          )
        }
        style={style}
      >
        {this.renderTitle()}
        {this.renderUserStatistics()}
        {this.renderTable()}
        <Button
          className={styles.exportBtn}
          type="primary"
          size="small"
          onClick={this.exportToCSV}
        >
          Export runs to CSV
        </Button>
      </div>
    );
  }
}

UserInfoSummary.propTypes = {
  className: PropTypes.string,
  user: PropTypes.object,
  style: PropTypes.object
};
