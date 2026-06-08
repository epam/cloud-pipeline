import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Menu} from 'antd';
import styles from './task-details.css';

function TaskDetailsTabs (props) {
  const {
    className,
    style,
    tabs,
    activeTabKey,
    onChangeActiveTabKey
  } = props;
  const onChange = ({key}) => onChangeActiveTabKey
    ? onChangeActiveTabKey(key)
    : undefined;
  return (
    <div
      className={classNames(
        className,
        styles.runDetailsTabs
      )}
      style={style}
    >
      <Menu
        className={classNames(styles.runDetailsTabsMenu, 'cp-run-details-tabs')}
        selectedKeys={activeTabKey ? [activeTabKey] : []}
        style={{cursor: 'pointer'}}
        mode="horizontal"
        onClick={onChange}
        items={tabs.map((tab) => ({
          key: tab.tab,
          id: tab.tab,
          label: (
            <div className={styles.runDetailsTabsMenuItem}>
              {tab.icon}
              <span>{tab.title}</span>
            </div>
          )
        }))}
      />
    </div>
  );
}

TaskDetailsTabs.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  task: PropTypes.object,
  tabs: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  activeTabKey: PropTypes.string,
  onChangeActiveTabKey: PropTypes.func
};

export default TaskDetailsTabs;
