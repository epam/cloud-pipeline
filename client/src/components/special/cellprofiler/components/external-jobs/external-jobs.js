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
import classNames from 'classnames';
import {Icon} from 'antd';
import {computed} from 'mobx';
import {observer} from 'mobx-react';
import Collapse from '../collapse';
import {getExternalEvaluations} from '../../model/analysis/external-evaluations';
import CellProfilerExternalJob from './cell-profiler-external-job';
import UserName from '../../../UserName';
import {compareUserNames, compareUserNamesWithoutDomain} from '../../../../../utils/users-filters';
import roleModel from '../../../../../utils/roleModel';

function parseFilters (filters = {}) {
  const {
    userNames = [],
    source
  } = filters;
  return {
    hcsFile: source,
    owners: userNames
  };
}

function filterByOwner (owners, owner) {
  if (!owners || owners.length === 0) {
    return true;
  }
  return owners.some(o => compareUserNames(o, owner) || compareUserNamesWithoutDomain(o, owner));
}

function jobArraysAreTheSame (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const aIds = [...new Set(a.map(job => job.id))].sort();
  const bIds = [...new Set(b.map(job => job.id))].sort();
  if (aIds.length !== bIds.length) {
    return false;
  }
  for (let i = 0; i < aIds.length; i++) {
    if (aIds[i] !== bIds[i]) {
      return false;
    }
  }
  return true;
}

class CellProfilerExternalJobs extends React.Component {
  state = {
    pending: false,
    jobs: [],
    expanded: false
  };

  @computed
  get currentUserName () {
    const {
      authenticatedUserInfo
    } = this.props;
    if (!authenticatedUserInfo.loaded || !authenticatedUserInfo.value) {
      return undefined;
    }
    return authenticatedUserInfo.value.userName;
  }

  get currentUserSelected () {
    const {
      owners = []
    } = parseFilters(this.props.filters);
    const currentUserName = this.currentUserName;
    if (!currentUserName) {
      return false;
    }
    return owners.some(o => o.toLowerCase() === currentUserName.toLowerCase());
  }

  get filteredJobs () {
    const {
      owners = []
    } = parseFilters(this.props.filters);
    const {
      jobs = []
    } = this.state;
    return jobs.filter(aJob => filterByOwner(owners, aJob.owner));
  }

  get currentUserJobs () {
    if (!this.currentUserName) {
      return [];
    }
    const {
      jobs = []
    } = this.state;
    return jobs.filter(aJob => aJob.owner && filterByOwner([this.currentUserName], aJob.owner));
  }

  get otherUserJobs () {
    if (!this.currentUserName) {
      return [];
    }
    const {
      jobs = []
    } = this.state;
    return jobs.filter(aJob => !aJob.owner || !filterByOwner([this.currentUserName], aJob.owner));
  }

  componentDidMount () {
    this.fetchJobs();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const prevFilters = parseFilters(prevProps.filters);
    const currentFilters = parseFilters(this.props.filters);
    if (prevFilters.hcsFile !== currentFilters.hcsFile) {
      this.fetchJobs();
    }
  }

  fetchJobs = () => {
    const {hcsFile} = parseFilters(this.props.filters);
    if (hcsFile) {
      this.setState({
        pending: true,
        jobs: []
      }, async () => {
        const state = {
          pending: false,
          jobs: []
        };
        try {
          state.jobs = await getExternalEvaluations(hcsFile);
        } catch (error) {
          console.log(error.message);
        } finally {
          this.setState(state);
        }
      });
    } else {
      this.setState({
        pending: false,
        jobs: [],
        expanded: false
      });
    }
  };

  onChangeExpanded = () => {
    const {expanded} = this.state;
    this.setState({
      expanded: !expanded
    });
  }

  handleChangeOwnersFilters = (newOwners = []) => {
    const {
      onChangeFilters,
      filters = {}
    } = this.props;
    if (typeof onChangeFilters === 'function') {
      const {
        userNames,
        ...restFilters
      } = filters;
      onChangeFilters({...restFilters, userNames: newOwners});
      this.setState({expanded: true});
    }
  };

  handleShowOtherJobs = (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    this.handleChangeOwnersFilters();
  };

  handleShowCurrentUserJobs = (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    this.handleChangeOwnersFilters([this.currentUserName].filter(Boolean));
  };

  renderShowMyJobsButton = () => {
    /*
    if:
    a) current user is not selected AND
    b) there are current user jobs AND
    c) there are other user jobs OR current filtered jobs are empty
     */
    const {
      pending
    } = this.state;
    const currentUserJobs = this.currentUserJobs;
    const filteredJobs = this.filteredJobs;
    const otherUserJobs = this.otherUserJobs;
    if (
      !pending &&
      !this.currentUserSelected &&
      currentUserJobs.length > 0 &&
      (otherUserJobs.length > 0 || filteredJobs.length === 0)
    ) {
      return (
        <div>
          <a onClick={this.handleShowCurrentUserJobs}>
            {/* eslint-disable-next-line max-len */}
            Show {currentUserJobs.length} my job{currentUserJobs.length > 1 ? 's' : ''}
          </a>
        </div>
      );
    }
    return null;
  };

  renderShowAllJobsButton = () => {
    /*
    if:
    a) current filtered jobs are not the same as all jobs AND
    b) all jobs are not empty
    c) all jobs are not current user's jobs
     */
    const {
      pending,
      jobs = []
    } = this.state;
    const filteredJobs = this.filteredJobs;
    const currentUserJobs = this.currentUserJobs;
    if (
      pending ||
      jobArraysAreTheSame(jobs, filteredJobs) ||// current filtered jobs are the same as all jobs
      jobs.length === 0 ||// all jobs are empty
      jobArraysAreTheSame(jobs, currentUserJobs)// all jobs are the same as current user's jobs
    ) {
      return null;
    }
    return (
      <div>
        <a onClick={this.handleShowOtherJobs}>
          {/* eslint-disable-next-line max-len */}
          Show all {jobs.length} job{jobs.length > 1 ? 's' : ''}
        </a>
      </div>
    );
  };

  render () {
    const {
      pending,
      expanded
    } = this.state;
    const {
      className,
      style,
      selected,
      onSelect
    } = this.props;
    const {
      hcsFile,
      owners = []
    } = parseFilters(this.props.filters);
    if (!hcsFile) {
      return null;
    }

    const filteredJobs = this.filteredJobs;

    return (
      <Collapse
        className={className}
        style={style}
        expanded={expanded && filteredJobs.length > 0}
        onExpandedChange={this.onChangeExpanded}
        empty={filteredJobs.length === 0}
        header={(
          <div>
            <b>External jobs</b>
            {
              !pending && filteredJobs.length > 0 && (
                <span>
                  {': '}
                  {filteredJobs.length}
                </span>
              )
            }
            {
              pending && (
                <Icon
                  type="loading"
                  style={{marginLeft: 5}}
                />
              )
            }
            {
              !pending && filteredJobs.length === 0 && (
                <span>
                  {' '}
                  not found for <i>{hcsFile}</i> file
                  {
                    owners.length > 0 && (
                      <span>
                        {' '}and owner{owners.length > 1 ? 's' : ''}
                        {' '}
                      </span>
                    )
                  }
                  {
                    owners.map(owner => (
                      <UserName
                        key={owner}
                        userName={owner}
                        style={{marginRight: 5}}
                        showIcon
                      />
                    ))
                  }
                </span>
              )
            }
            {this.renderShowMyJobsButton()}
            {this.renderShowAllJobsButton()}
          </div>
        )}
      >
        {
          filteredJobs.map((aJob) => (
            <CellProfilerExternalJob
              key={aJob.id}
              job={aJob}
              className={
                classNames(
                  'cell-profiler-job',
                  {
                    'selected': aJob.id === selected
                  }
                )
              }
              onClick={() => onSelect(aJob)}
            />
          ))
        }
      </Collapse>
    );
  }
}

CellProfilerExternalJobs.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  selected: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  onSelect: PropTypes.func,
  filters: PropTypes.object,
  onChangeFilters: PropTypes.func
};

export default roleModel.authenticationInfo(observer(CellProfilerExternalJobs));
