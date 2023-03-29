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
import {observer} from 'mobx-react';
import {Icon} from 'antd';
import styles from './cell-profiler.css';

@observer
class Collapse extends React.Component {
  state = {
    expanded: false
  };

  toggleExpanded = () => {
    this.setState({
      expanded: !this.state.expanded
    });
  };

  renderHeader () {
    const {
      onExpandedChange = this.toggleExpanded,
      header,
      empty
    } = this.props;
    if (!header) {
      return null;
    }
    const render = () => {
      if (typeof header === 'string') {
        return (
          <b>
            {header}
          </b>
        );
      }
      if (typeof header === 'function') {
        return header(this.state.expanded);
      }
      return header;
    };
    return (
      <div
        className={
          classNames(
            styles.cellProfilerCollapseHeader,
            'cell-profiler-module-header'
          )
        }
        onClick={onExpandedChange}
      >
        {
          !empty && (
            <Icon
              type="right"
              className={styles.expandIndicator}
            />
          )
        }
        {render()}
      </div>
    );
  }

  render () {
    const {
      expanded = this.state.expanded,
      children,
      empty,
      className,
      unmountOnClose = true,
      style
    } = this.props;
    return (
      <div
        className={
          classNames(
            styles.cellProfilerCollapse,
            'cell-profiler-module',
            {
              expanded,
              empty,
              [styles.expanded]: expanded,
              [styles.empty]: empty
            },
            className
          )
        }
        style={style}
      >
        {
          this.renderHeader()
        }
        {
          (expanded || !unmountOnClose) && !empty && (
            <div
              className={styles.cellProfilerCollapseContent}
              style={!expanded ? {display: 'none'} : {}}
            >
              {children}
            </div>
          )
        }
      </div>
    );
  }
}

Collapse.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  header: PropTypes.oneOfType([PropTypes.node, PropTypes.func]),
  expanded: PropTypes.bool,
  onExpandedChange: PropTypes.func,
  empty: PropTypes.bool,
  unmountOnClose: PropTypes.bool
};

export default Collapse;
