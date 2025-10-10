import React from 'react';
import PropTypes from 'prop-types';
import {Icon, Menu, Dropdown} from 'antd';
import classNames from 'classnames';
import styles from './logs-mode.css';

class LogsModeButton extends React.Component {
  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.current !== prevProps.current
    ) {
      this.closeMenu();
    }
  }

  render () {
    const {
      className,
      style,
      modes = [],
      onChangeMode,
      current,
      bordered
    } = this.props;
    if ((modes || []).length <= 1 || !onChangeMode) {
      return null;
    }
    const onClick = ({key}) => {
      if (key !== current) {
        onChangeMode(key);
      }
    };
    const menu = (
      <Menu onClick={onClick}>
        {
          modes.map((mode) => (
            <Menu.Item key={mode}>
              <div
                style={{display: 'flex', alignItems: 'center', minWidth: 120}}
              >
                <span style={current === mode ? {fontWeight: 'bold', flex: 1} : {flex: 1}}>
                  {mode}
                </span>
                {
                  current === mode && <Icon type="check" />
                }
              </div>
            </Menu.Item>
          ))
        }
      </Menu>
    );
    return (
      <div
        className={classNames(className, styles.logsModeButton, {
          'cp-bordered': bordered
        })}
        style={style}
      >
        <Dropdown overlay={menu}>
          <a className="cp-text cp-text-not-important">
            <Icon type="appstore-o" style={{marginRight: 3}} />
            <b>{current ?? 'view'}</b>
            <span>{' view'}</span>
            <Icon type="down" />
          </a>
        </Dropdown>
      </div>
    );
  }
}

LogsModeButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  modes: PropTypes.arrayOf(PropTypes.string),
  onChangeMode: PropTypes.func,
  current: PropTypes.string,
  bordered: PropTypes.bool
};

export default LogsModeButton;
