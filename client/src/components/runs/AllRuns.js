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
import {observable} from 'mobx';
import {Card, Col, Menu, Row} from 'antd';
import classNames from 'classnames';
import RunTable, {Columns} from './run-table';
import SessionStorageWrapper from '../special/SessionStorageWrapper';
import AdaptedLink from '../special/AdaptedLink';
import roleModel from '../../utils/roleModel';
import parseQueryParameters from '../../utils/queryParameters';
import LoadingView from '../special/LoadingView';
import {RunCountDefault} from '../../models/pipelines/RunCount';
import continuousFetch from '../../utils/continuous-fetch';
import styles from './AllRuns.css';

const getStatusForServer = active => active
  ? ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING']
  : ['SUCCESS', 'FAILURE', 'STOPPED'];

@roleModel.authenticationInfo
@inject('counter')
@inject(({routing}, {params}) => {
  const {
    status = 'active'
  } = params;
  const query = parseQueryParameters(routing);
  const all = query.hasOwnProperty('all') && /^(true|undefined)$/i.test(`${query.all}`);
  const active = /^active$/i.test(status);
  return {
    active,
    all
  };
})
@observer
class AllRuns extends React.Component {
  @observable allRunsCounter;

  componentDidMount () {
    this.allRunsCounter = new RunCountDefault(this.props.counter);
    this.startCounter();
  }

  componentWillUnmount () {
    if (this.allRunsCounter && typeof this.allRunsCounter.destroy === 'function') {
      this.allRunsCounter.destroy();
    }
    this.stopCounter();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.active !== this.props.active) {
      this.startCounter();
    }
  }

  startCounter = () => {
    this.stopCounter();
    const {
      active
    } = this.props;
    if (active) {
      const {
        stop
      } = continuousFetch({request: this.allRunsCounter});
      this.stop = stop;
    }
  };

  stopCounter = () => {
    if (typeof this.stop === 'function') {
      this.stop();
    }
    this.stop = undefined;
  };

  navigateToActiveRuns = (my = false) => {
    SessionStorageWrapper.setItem(SessionStorageWrapper.ACTIVE_RUNS_KEY, my);
    SessionStorageWrapper.navigateToActiveRuns(this.props.router);
  };

  renderOwnersSwitch = (total) => {
    const {
      active,
      all
    } = this.props;
    if (
      !active
    ) {
      return null;
    }
    if (all) {
      return (
        <Row style={{marginBottom: 5, padding: 2}}>
          Currently viewing <b>all available active runs</b>.
          {' '}
          <a onClick={() => this.navigateToActiveRuns(true)}>
            View only <b>your active runs</b>
          </a>
        </Row>
      );
    }
    let totalInfo = '';
    const allRunsCount = this.allRunsCounter
      ? this.allRunsCounter.runsCount
      : 0;
    if (total > 0 && total < allRunsCount) {
      totalInfo = ` (${total} out of ${allRunsCount})`;
    }
    return (
      <Row style={{marginBottom: 5, padding: 2}}>
        Currently viewing only
        {' '}
        <b>
          your active runs
          {totalInfo}
        </b>.
        {' '}
        <a
          onClick={() => this.navigateToActiveRuns(false)}
        >
          View <b>other available active runs</b>
        </a>
      </Row>
    );
  };

  renderTable = () => {
    const {
      active,
      all,
      authenticatedUserInfo
    } = this.props;
    const filters = {
      statuses: getStatusForServer(active)
    };
    if (
      active &&
      !all &&
      !authenticatedUserInfo.loaded &&
      authenticatedUserInfo.pending
    ) {
      return (
        <LoadingView />
      );
    }
    if (active && !all && authenticatedUserInfo.loaded) {
      filters.owners = [authenticatedUserInfo.value.userName].filter(Boolean);
    }
    return (
      <RunTable
        filters={filters}
        autoUpdate={active}
        disableFilters={active && !all ? [Columns.owner] : []}
        beforeTable={({total}) => this.renderOwnersSwitch(total)}
      />
    );
  };

  render () {
    const {
      active
    } = this.props;
    return (
      <Card
        className={
          classNames(
            styles.runsCard,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
        bodyStyle={{padding: 15}}
      >
        <Row type="flex" align="bottom">
          <Col offset={2} span={20}>
            <Row type="flex" justify="center">
              <Menu
                mode="horizontal"
                selectedKeys={active ? ['active'] : ['completed']}
                className={styles.tabsMenu}
              >
                <Menu.Item key="active">
                  <AdaptedLink
                    id="active-runs-button"
                    to={SessionStorageWrapper.getActiveRunsLink()}
                    location={location}>Active Runs
                    {
                      this.allRunsCounter
                        ? ` (${this.allRunsCounter.runsCount})`
                        : ''
                    }
                  </AdaptedLink>
                </Menu.Item>
                <Menu.Item key="completed">
                  <AdaptedLink
                    id="completed-runs-button"
                    to={'/runs/completed'}
                    location={location}>Completed Runs</AdaptedLink>
                </Menu.Item>
              </Menu>
            </Row>
          </Col>
          <Col
            span={2}
            type="flex"
            style={{textAlign: 'right', padding: 5, textTransform: 'uppercase'}}>
            <AdaptedLink
              id="advanced-runs-filter-button"
              to={'/runs/filter'}
              location={location}>
              Advanced filter
            </AdaptedLink>
          </Col>
        </Row>
        {
          this.renderTable()
        }
      </Card>
    );
  }
}

export default AllRuns;
