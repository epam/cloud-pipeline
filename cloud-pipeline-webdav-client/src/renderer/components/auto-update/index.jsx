import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import { CloseOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import useAutoUpdate from './use-auto-update';
import './auto-update.css';

function AutoUpdate(
  {
    className,
    style,
  },
) {
  const {
    available,
    appName,
    ignore,
    pending,
    update,
    error,
  } = useAutoUpdate();
  if (!available) {
    return null;
  }
  return (
    <div
      className={
        classNames(
          'auto-update-container',
          className,
        )
      }
      style={style}
    >
      <div
        className="update-notification"
      >
        <CloseOutlined
          className="close"
          onClick={ignore}
        />
        {
          pending && (
            <div>
              <b>{appName}</b>
              {' is updating now. Do not close the window.'}
            </div>
          )
        }
        {
          !pending && (
            <div>
              {'New version of '}
              <b>{appName}</b>
              {' is available. '}
              {/* eslint-disable-next-line jsx-a11y/anchor-is-valid */}
              <Button
                type="link"
                onClick={update}
                style={{ padding: 4 }}
              >
                Install updates
              </Button>
            </div>
          )
        }
        {
          error && (
            <div
              className="update-error"
            >
              {error}
            </div>
          )
        }
      </div>
    </div>
  );
}

AutoUpdate.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

AutoUpdate.defaultProps = {
  className: undefined,
  style: undefined,
};

export default AutoUpdate;
