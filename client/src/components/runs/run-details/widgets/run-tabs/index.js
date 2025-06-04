import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Button, Icon, Menu} from 'antd';
import styles from './run-tabs.css';

function RunTabs (props) {
  const {
    className,
    style,
    tab: currentTab,
    onTabChange,
    tabs = [],
    run
  } = props;
  if (tabs.length < 2) {
    return null;
  }
  const onChange = ({key}) => onTabChange ? onTabChange(key) : undefined;
  return (
    <div
      className={classNames(className, styles.runTabs, 'cp-run-details-tabs')}
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
              <div className={styles.runTabsMenuItem}>
                {tab.icon}
                <span>{tab.title}</span>
                {tab.action && (
                  <div style={{display: 'inline-flex', alignItems: 'center', marginLeft: 10}}>
                    {typeof tab.action === 'function' ? tab.action({run}) : tab.action}
                  </div>
                )}
              </div>
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
  tabs: PropTypes.array,
  onTabChange: PropTypes.func
};

export default RunTabs;
