/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import styles from './system-logs.css';
import Filters from './filters';
import Logs from './logs';

const SIZE_UPDATER_DELAY = 500;

class SystemLogs extends React.Component {
  state = {
    filters: {},
    logContainerSize: {
      width: undefined,
      height: undefined
    },
    logContainerInitialized: false,
    filtersInitialized: false
  };

  filters;
  logsScrollContainer;
  sizeUpdater;

  componentDidUpdate (prevProps, prevState, snapshot) {
    this.updateSize();
  }

  componentDidMount () {
    this.updateSize();
    this.sizeUpdater = setInterval(this.updateSize, SIZE_UPDATER_DELAY);
    window.addEventListener('resize', this.updateSize);
  }

  componentWillUnmount () {
    clearInterval(this.sizeUpdater);
    window.removeEventListener('resize', this.updateSize);
  }

  onFiltersInitialized = (filters) => {
    this.filters = filters;
    setTimeout(() => {
      this.setState({filtersInitialized: true});
    }, SIZE_UPDATER_DELAY);
  };

  onLogsScrollContainerInitialized = (container) => {
    this.logsScrollContainer = container;
    setTimeout(() => {
      this.setState({logContainerInitialized: true});
    }, SIZE_UPDATER_DELAY);
  };

  updateSize = () => {
    const {logContainerInitialized, filtersInitialized} = this.state;
    if (
      logContainerInitialized &&
      filtersInitialized &&
      this.logsScrollContainer
    ) {
      requestAnimationFrame(() => {
        const width = this.logsScrollContainer.clientWidth;
        const height = this.logsScrollContainer.clientHeight;
        const {logContainerSize} = this.state;
        const {width: currentWidth, height: currentHeight} = logContainerSize;
        if (currentHeight !== height || currentWidth !== width) {
          this.setState({
            logContainerSize: {
              width,
              height
            }
          });
        }
      });
    }
  };

  onFiltersChange = (newFilters) => {
    console.log(newFilters);
    this.setState({
      filters: newFilters
    });
  };

  render () {
    const {
      filters,
      logContainerSize
    } = this.state;
    return (
      <div className={styles.container}>
        <Filters
          filters={filters}
          onChange={this.onFiltersChange}
          onInitialized={this.onFiltersInitialized}
          onExpand={this.updateSize}
        />
        <div className={styles.logsContainer}>
          <div
            className={styles.logsScrollContainer}
            ref={this.onLogsScrollContainerInitialized}
          >
            <Logs
              filters={filters}
              height={logContainerSize.height}
            />
          </div>
        </div>
      </div>
    );
  }
}

export default SystemLogs;
