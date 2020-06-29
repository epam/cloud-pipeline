import React, {useCallback, useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {Breadcrumb, Input} from 'antd';
import classNames from 'classnames';
import './path-navigation.css';

function PathNavigation ({path, onNavigate, fileSystem}) {
  const [active, setActive] = useState(false);
  const [editPath, setEditPath] = useState(path);
  useEffect(() => {
    setActive(false);
    setEditPath(path);
  }, [path]);
  const becomeActive = useCallback(() => {
    setActive(true);
  }, [setActive]);
  const becomeInactive = useCallback(() => {
    setActive(false);
  }, [setActive]);
  const navigate = useCallback((path) => {
    if (onNavigate) {
      onNavigate(path);
      becomeInactive();
    }
  }, [onNavigate]);
  const onClick = useCallback((part, e) => {
    if (part.isCurrent) {
      becomeActive();
    } else {
      e && e.stopPropagation();
      navigate(part.path);
    }
  }, [becomeActive, navigate]);
  if (!fileSystem) {
    return null;
  }
  if (active) {
    return (
      <Input
        autoFocus
        className="path-navigation-input"
        value={editPath}
        onChange={e => setEditPath(e.target.value)}
        onPressEnter={() => navigate(editPath)}
        onBlur={becomeInactive}
      />
    );
  }
  const parts = [
    {
      name: fileSystem.rootName,
      path: ''
    }
  ]
    .concat(
      fileSystem.parsePath(path, true)
        .filter(Boolean)
        .map((name, index, array) => ({
          name,
          path: fileSystem.joinPath(...array.slice(0, index + 1))
        }))
    )
    .map((item, index, array) => ({
      ...item,
      isCurrent: array.length - 1 === index
    }));
  return (
    <Breadcrumb className="path-navigation" onClick={() => onClick(parts[parts.length - 1])}>
      {
        parts.map(part => (
          <Breadcrumb.Item
            key={part.path}
            className={
              classNames(
                'part',
                {
                  current: part.isCurrent
                })
            }
            onClick={(e) => onClick(part, e)}
            separator={fileSystem.separator}
          >
            {part.name}
          </Breadcrumb.Item>
        ))
      }
    </Breadcrumb>
  );
}

PathNavigation.propTypes = {
  path: PropTypes.string,
  onNavigate: PropTypes.func,
  fileSystem: PropTypes.object,
};

export default PathNavigation;
