import React from 'react';
import {Popover} from 'antd';
import {PathList} from './path-list';
import classNames from 'classnames';

import styles from './path-cell.css';

const DISPLAY_LIMIT = 5;

class PathCell extends React.PureComponent {
  state = {
    moreVisible: false
  };

  onCloseMorePaths = () => this.setState({moreVisible: false});

  onShowMoreVisibilityChanged = (visible) => this.setState({moreVisible: visible});

  get filteredPaths () {
    const {paths, search} = this.props;
    if (!search) {
      return paths;
    }
    const filtered = paths.filter(path => path
      .toLowerCase()
      .includes((search || '').toLowerCase())
    );
    return filtered.length === 0 ? paths : filtered;
  }

  get hasMoreToShow () {
    return this.filteredPaths.length > DISPLAY_LIMIT;
  }

  get visiblePaths () {
    return this.hasMoreToShow
      ? this.filteredPaths.slice(0, DISPLAY_LIMIT)
      : this.filteredPaths;
  }

  get restPaths () {
    return this.hasMoreToShow
      ? this.filteredPaths.slice(DISPLAY_LIMIT, this.filteredPaths.length)
      : [];
  }

  render () {
    const {
      paths = [],
      rule,
      search
    } = this.props;
    const {moreVisible} = this.state;
    const showMoreAmount = this.restPaths.length;
    const filreredAmount = paths.length - this.filteredPaths.length;
    const filteredCountText = `+${filreredAmount} filtered`;
    const showMoreText = `+${showMoreAmount} more`;
    return (
      <div>
        <PathList
          paths={this.visiblePaths}
          rule={rule}
          onPreviewVisibilityChanged={this.onCloseMorePaths}
          search={search}
        />
        {this.hasMoreToShow && (
          <Popover
            content={(
              <div className={styles.pathsPopoverContent}>
                <PathList
                  paths={this.restPaths}
                  rule={rule}
                  onPreviewVisibilityChanged={this.onCloseMorePaths}
                  search={search}
                />
              </div>)}
            trigger="click"
            visible={moreVisible}
            onVisibleChange={this.onShowMoreVisibilityChanged}
          >
            <a className={styles.showMorePathButton}>
              {showMoreText}
            </a>
          </Popover>
        )}
        {filreredAmount > 0 ? (
          <span
            className={classNames('cp-text-not-important', styles.filteredCount)}
          >
            {filteredCountText}
          </span>
        ) : null}
      </div>
    );
  }
}

export {PathCell};
