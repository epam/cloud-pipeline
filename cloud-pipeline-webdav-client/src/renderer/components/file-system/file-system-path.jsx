import React, {
  useCallback,
  useContext,
  useState,
} from 'react';
import PropTypes from 'prop-types';
import { Breadcrumb, Input } from 'antd';
import classNames from 'classnames';
import { FileSystemPathContext } from './hooks/use-file-system-path';
import usePathBreadcrumbs from './hooks/use-path-breadcrumbs';
import FileSystemSelector from './file-system-selector';
import './file-system-path.css';

function FileSystemPath(
  {
    className,
    style,
  },
) {
  const {
    items: breadcrumbs,
    separator,
  } = usePathBreadcrumbs();
  const [active, setActive] = useState(false);
  const [editPath, setEditPath] = useState();
  const {
    path,
    onChangePath,
  } = useContext(FileSystemPathContext);
  const becomeActive = useCallback(() => {
    setActive(true);
    setEditPath(path);
  }, [setActive, path]);
  const becomeInactive = useCallback(() => {
    setActive(false);
  }, [setActive]);
  const navigate = useCallback((newPath) => {
    onChangePath(newPath);
    becomeInactive();
  }, [onChangePath, becomeInactive]);
  const onClick = useCallback((part, e) => {
    if (part.isCurrent) {
      becomeActive();
      return;
    }
    if (e) {
      e.stopPropagation();
    }
    navigate(part.path);
  }, [becomeActive, navigate]);
  const onEdit = useCallback((event) => setEditPath(event.target.value), [setEditPath]);
  const onPressEnter = useCallback(() => {
    navigate(editPath);
  }, [editPath, navigate]);
  if (active) {
    return (
      <div
        className={
          classNames(
            'path-navigation',
            className,
          )
        }
        style={style}
      >
        <Input
          autoFocus
          className="path-navigation-input"
          value={editPath}
          onChange={onEdit}
          onPressEnter={onPressEnter}
          onBlur={becomeInactive}
        />
      </div>
    );
  }
  return (
    <div
      className={
        classNames(
          'path-navigation',
          className,
        )
      }
      style={style}
    >
      <Breadcrumb
        onClick={becomeActive}
        separator={separator}
      >
        {
          breadcrumbs.map((part, idx) => (
            <Breadcrumb.Item
              key={part.path}
              className={
                classNames(
                  'part',
                  {
                    current: part.isCurrent,
                  },
                )
              }
              onClick={(e) => onClick(part, e)}
            >
              {part.name}
              {
                idx === 0 && (
                  <FileSystemSelector
                    className="file-system-selector"
                  />
                )
              }
            </Breadcrumb.Item>
          ))
        }
      </Breadcrumb>
    </div>
  );
}

FileSystemPath.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

FileSystemPath.defaultProps = {
  className: undefined,
  style: undefined,
};

export default FileSystemPath;
