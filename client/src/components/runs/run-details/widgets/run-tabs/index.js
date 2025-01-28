import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Menu} from 'antd';
import styles from './run-tabs.css';
import {getRunTabs} from './tabs';

function RunTabs (props) {
  const {
    className,
    style,
    tab: currentTab,
    onTabChange,
    run
  } = props;
  const tabs = getRunTabs(run);
  if (tabs.length < 2) {
    return null;
  }
  const onChange = ({key}) => onTabChange ? onTabChange(key) : undefined;
  return (
    <div
      className={classNames(className, styles.runTabs)}
      style={style}
    >
      <Menu
        className={styles.runTabsMenu}
        selectedKeys={[currentTab]}
        style={{cursor: 'pointer'}}
        mode="horizontal"
        onClick={onChange}
      >
        {
          tabs.map((tab) => (
            <Menu.Item
              key={tab.tab}
              id={tab.tab}
            >
              {tab.title}
            </Menu.Item>
          ))
        }
      </Menu>
    </div>
  );
}

RunTabs.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  tab: PropTypes.string,
  onTabChange: PropTypes.func
};

export default RunTabs;
