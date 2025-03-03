import React from 'react';
import {Popover} from 'antd';
import {PathList} from './path-list';

import styles from './path-cell.css';

const DISPLAY_LIMIT = 5;

class PathCell extends React.PureComponent {
  state = {
    moreVisible: false
  };

  onCloseMorePaths = () => this.setState({moreVisible: false});

  onShowMoreVisibilityChanged = (visible) => this.setState({moreVisible: visible});

  render () {
    const {
      paths = [],
      rule
    } = this.props;
    const {moreVisible} = this.state;
    const hasMoreToShow = paths.length > DISPLAY_LIMIT;
    const visiblePaths = hasMoreToShow ? paths.slice(0, DISPLAY_LIMIT) : paths;
    const showMoreAmount = paths.length - DISPLAY_LIMIT;
    const showMoreText = `+ ${showMoreAmount} more`;

    return (
      <div>
        <PathList
          paths={visiblePaths}
          rule={rule}
          onPreviewVisibilityChanged={this.onCloseMorePaths}
        />
        {hasMoreToShow && (
          <Popover
            content={(
              <div className={styles.pathsPopoverContent}>
                <PathList
                  paths={paths}
                  rule={rule}
                  onPreviewVisibilityChanged={this.onCloseMorePaths}
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
      </div>
    );
  }
}

export {PathCell};
