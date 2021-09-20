/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {CaretLeftOutlined, CaretRightOutlined, FilterOutlined} from '@ant-design/icons';
import {Alert, Badge, Button, Spin} from 'antd';
import classNames from 'classnames';
import CommitCard from './commit-card';
import HistoryFilter from './history-filter';
import LoadVSCommits from '../../../../../models/versioned-storage/load-commits';
import styles from './history.css';

const PAGE_SIZE = 20;

const Badged = ({enabled = false, children}) => {
  if (enabled) {
    return (
      <Badge
        dot
      >
        {children}
      </Badge>
    );
  }
  return children;
};

class VSHistory extends React.Component {
  state = {
    page: 0,
    pageSize: PAGE_SIZE,
    filters: undefined,
    error: undefined,
    commits: [],
    pending: false,
    hasMorePages: false,
    filtersVisible: false
  };

  get canNavigateToPreviousPage () {
    const {page} = this.state;
    return page > 0;
  }

  get canNavigateToNextPage () {
    const {hasMorePages} = this.state;
    return hasMorePages;
  }

  get filtersEnabled () {
    const {filters} = this.state;
    return !!filters && (
      (filters.authors || []).length > 0 ||
      (filters.extensions || []).length > 0 ||
      !!filters.dateFrom ||
      !!filters.dateTo
    );
  }

  get path () {
    const {
      path: rawPath,
      isFolder
    } = this.props;
    let path = rawPath;
    if (rawPath && isFolder && !rawPath.endsWith('/')) {
      path = rawPath.concat('/');
    } else if (rawPath && !isFolder && rawPath.endsWith('/')) {
      path = rawPath.slice(0, -1);
    }
    return path;
  }

  componentDidMount () {
    this.fetchCommits();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.path !== this.props.path ||
      prevProps.versionedStorageId !== this.props.versionedStorageId ||
      prevProps.isFolder !== this.props.isFolder ||
      prevProps.revision !== this.props.revision
    ) {
      this.clearFiltersAndFetchCommits();
    }
  }

  clearFiltersAndFetchCommits = () => {
    this.setState({
      page: 0,
      pageSize: PAGE_SIZE,
      filters: undefined,
      error: undefined,
      commits: [],
      pending: false,
      hasMorePages: false
    }, this.fetchCommits);
  };

  navigateToPreviousPage = () => {
    if (this.canNavigateToPreviousPage) {
      const {page} = this.state;
      this.onPageChanged(page - 1);
    }
  };

  navigateToNextPage = () => {
    if (this.canNavigateToNextPage) {
      const {page} = this.state;
      this.onPageChanged(page + 1);
    }
  };

  onPageChanged = (page) => {
    if (this.state.page !== page) {
      this.setState({
        page
      }, this.fetchCommits);
    }
  };

  onFiltersChanged = (filters) => {
    this.setState({
      filters,
      filtersVisible: false
    }, this.fetchCommits);
  };

  openFilters = () => {
    this.setState({filtersVisible: true});
  };

  closeFilters = () => {
    this.setState({filtersVisible: false});
  };

  fetchCommits = () => {
    const {
      versionedStorageId
    } = this.props;
    if (!versionedStorageId) {
      return;
    }
    this.setState({
      pending: true
    }, () => {
      const done = (statePayload) => {
        this.setState({
          hasMorePages: !(statePayload?.error),
          ...statePayload,
          pending: false
        });
      };
      const {
        page,
        pageSize,
        filters
      } = this.state;
      const request = new LoadVSCommits(
        versionedStorageId,
        page,
        pageSize
      );
      const filtersPayload = {
        path: this.path,
        ...(filters || {})
      };
      request
        .send(filtersPayload)
        .then(() => {
          if (request.loaded) {
            const {
              has_next: hasMorePages = false,
              listing: commits = []
            } = request.value;
            done({commits, error: undefined, hasMorePages});
          } else {
            done({error: request.message || 'Error fetching commits'});
          }
        })
        .catch(e => {
          done({error: e.message});
        });
    });
  };

  render () {
    const {
      className,
      style,
      versionedStorageId
    } = this.props;
    if (!versionedStorageId) {
      return null;
    }
    const {
      commits,
      pending,
      error,
      filtersVisible,
      filters
    } = this.state;
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
        <div
          className={styles.header}
          style={{
            paddingRight: 5
          }}
        >
          <div className={styles.title}>
            Revision history
          </div>
          <Badged
            enabled={this.filtersEnabled}
          >
            <Button
              className={styles.filter}
              size="small"
              disabled={pending}
              onClick={this.openFilters}
            >
              <FilterOutlined />
            </Button>
          </Badged>
          <HistoryFilter
            visible={filtersVisible}
            filters={filters}
            onCancel={this.closeFilters}
            onChange={this.onFiltersChanged}
            userNames={[]}
          />
        </div>
        {
          error && (
            <Alert
              type="error"
              message={error}
            />
          )
        }
        {
          pending && (
            <div
              style={{
                display: 'flex',
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <Spin />
            </div>
          )
        }
        <div className={styles.content}>
          {
            (commits || []).map(commit => (
              <CommitCard
                key={commit.commit}
                commit={commit}
                disabled={pending}
                versionedStorageId={versionedStorageId}
                path={this.path}
              />
            ))
          }
        </div>
        <div
          className={styles.pagination}
        >
          <Button
            className={styles.paginationButton}
            disabled={pending || !this.canNavigateToPreviousPage}
            size="small"
            onClick={this.navigateToPreviousPage}
          >
            <CaretLeftOutlined />
          </Button>
          <Button
            className={styles.paginationButton}
            disabled={pending || !this.canNavigateToNextPage}
            size="small"
            onClick={this.navigateToNextPage}
          >
            <CaretRightOutlined />
          </Button>
        </div>
      </div>
    );
  }
}

VSHistory.propTypes = {
  className: PropTypes.string,
  versionedStorageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  revision: PropTypes.string,
  path: PropTypes.string,
  isFolder: PropTypes.bool,
  style: PropTypes.object
};

export default VSHistory;
