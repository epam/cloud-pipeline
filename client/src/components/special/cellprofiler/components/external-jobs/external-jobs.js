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
import Collapse from '../collapse';
import {getExternalEvaluations} from '../../model/analysis/external-evaluations';
import CellProfilerOtherJob from './cell-profiler-other-job';
import UserName from '../../../UserName';
import {compareUserNames, compareUserNamesWithoutDomain} from '../../../../../utils/users-filters';

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

class CellProfilerExternalJobs extends React.PureComponent {
  state = {
    pending: false,
    jobs: [],
    expanded: false
  };

  get filteredJobs () {
    const {
      owners = []
    } = parseFilters(this.props.filters);
    const {
      jobs = []
    } = this.state;
    return jobs.filter(aJob => !aJob.owner || filterByOwner(owners, aJob.owner));
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

    return (
      <Collapse
        className={className}
        style={style}
        expanded={expanded && this.filteredJobs.length > 0}
        onExpandedChange={this.onChangeExpanded}
        empty={this.filteredJobs.length === 0}
        header={(
          <div>
            <b>External jobs</b>
            {
              pending && (
                <Icon
                  type="loading"
                  style={{marginLeft: 5}}
                />
              )
            }
            {
              !pending && this.filteredJobs.length === 0 && (
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
                      />
                    ))
                  }
                </span>
              )
            }
            {
              !pending && this.filteredJobs.length > 0 && (
                <span>
                  {': '}
                  {this.filteredJobs.length}
                </span>
              )
            }
          </div>
        )}
      >
        {
          this.filteredJobs.map((aJob) => (
            <CellProfilerOtherJob
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
  filters: PropTypes.object
};

export default CellProfilerExternalJobs;
