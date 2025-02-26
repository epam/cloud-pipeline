import React from 'react';
import {Button, Popover} from 'antd';
import {PathList} from './path-list';

import styles from './path-cell.css';

const DISPLAY_LIMIT = 2;

export const PathCell = ({paths = []}) => {
  const hasMoreToShow = paths.length > DISPLAY_LIMIT;
  const visiblePaths = hasMoreToShow ? paths.slice(0, DISPLAY_LIMIT) : paths;
  const showMoreAmount = paths.length - DISPLAY_LIMIT;
  const showMoreText = `+ ${showMoreAmount} more`;

  return (
    <div>
      <PathList paths={visiblePaths} />
      {hasMoreToShow && (
        <Popover
          content={(
            <div className={styles.pathsPopoverContent}>
              <PathList paths={paths} />
            </div>)}
          trigger="click"
        >
          <Button className={styles.showMorePathButton} size="small">
            {showMoreText}
          </Button>
        </Popover>
      )}
    </div>
  );
};
