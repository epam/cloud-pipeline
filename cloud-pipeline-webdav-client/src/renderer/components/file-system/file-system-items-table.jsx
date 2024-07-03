import React, {
  useCallback,
  useContext, useEffect, useMemo,
  useState,
} from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import { Alert, Spin } from 'antd';
import { FileSystemContentsContext } from './hooks/use-file-system-contents';
import {
  SplitPanelRow,
  SplitPanelColumn,
  SplitPanelGrid,
} from '../../common/split-panel';
import FileSystemElement from './file-system-element';
import DropTarget from './utilities/drag-n-drop/drop-target';
import { useCurrentPath } from './hooks/use-file-system-path';
import './file-system-contents.css';

const PAGE_SIZE = 100;

function FileSystemItemsTable(
  {
    className,
    style,
  },
) {
  const {
    pending,
    error,
    items,
  } = useContext(FileSystemContentsContext);
  const [slicer, setSlicer] = useState(0);
  const currentPath = useCurrentPath();
  useEffect(() => setSlicer(0), [currentPath, setSlicer]);
  const showMore = useCallback(() => setSlicer((s) => s + 1), [setSlicer]);
  const sliced = useMemo(
    () => (items || []).slice(0, (slicer + 1) * PAGE_SIZE),
    [slicer, items],
  );
  const hasMore = sliced.length < (items || []).length;
  const [hovered, setHovered] = useState(undefined);
  const onHover = useCallback((item) => {
    const { path } = item || {};
    setHovered(path);
  }, [setHovered]);
  if (error) {
    return (
      <div
        className={
          classNames(
            className,
            'directory-contents',
          )
        }
        style={style}
      >
        <Alert type="error" message={error} />
      </div>
    );
  }
  if (pending) {
    return (
      <div
        className="spin-container directory-contents"
      >
        <Spin />
      </div>
    );
  }
  return (
    <DropTarget dropOverClassName="drop-target">
      <SplitPanelGrid
        className={
          classNames(
            className,
            'directory-contents',
          )
        }
        style={style}
        resizable={false}
      >
        {
          sliced.map((item, index) => (
            <SplitPanelRow
              height={28}
              key={item.path}
            >
              <FileSystemElement
                element={item}
                hovered={item.path === hovered}
                onHover={onHover}
                even={index % 2 === 1}
              />
            </SplitPanelRow>
          ))
        }
        {
          hasMore && (
            <SplitPanelRow row="more" height={28}>
              <SplitPanelColumn
                spread
                className="show-more"
                onClick={showMore}
              >
                {sliced.length}
                {' items are shown out of '}
                {items.length}
                .
                <u style={{ marginLeft: 5 }}>Show more</u>
              </SplitPanelColumn>
            </SplitPanelRow>
          )
        }
      </SplitPanelGrid>
    </DropTarget>
  );
}

FileSystemItemsTable.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

FileSystemItemsTable.defaultProps = {
  className: undefined,
  style: {},
};

export default FileSystemItemsTable;
