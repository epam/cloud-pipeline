import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import { Button } from 'antd';
import useAutoUpdate from './use-auto-update';
import './auto-update.css';

function AutoUpdateButton(
  {
    className,
    style,
  },
) {
  const {
    appName,
    available,
    supported,
    update,
    check,
    checkPending,
    checkError,
    error: updateError,
    pending,
  } = useAutoUpdate();
  const error = updateError || checkError;
  if (!supported) {
    return null;
  }
  if (checkPending) {
    return (
      <div
        className={
          classNames(
            className,
            'auto-update-button-container',
          )
        }
        style={style}
      >
        <span>
          Checking for updates...
        </span>
      </div>
    );
  }
  if (pending) {
    return (
      <div
        className={
          classNames(
            className,
            'auto-update-button-container',
          )
        }
        style={style}
      >
        <span>
          Updating... do not close the application
        </span>
      </div>
    );
  }
  if (!available) {
    return (
      <>
        <div
          className={
            classNames(
              className,
              'auto-update-button-container',
            )
          }
          style={style}
        >
          <span>Current version is up-to-date.</span>
          <Button
            type="link"
            onClick={check}
          >
            Check for updates
          </Button>
        </div>
        {
          error && (
            <div className="update-error">
              {error}
            </div>
          )
        }
      </>
    );
  }
  if (available) {
    return (
      <>
        <div
          className={
            classNames(
              className,
              'auto-update-button-container',
            )
          }
          style={style}
        >
          <span>
            {'New version of '}
            <b>{appName}</b>
            {' is available.'}
          </span>
          <Button
            type="primary"
            onClick={update}
            style={{ marginLeft: 5 }}
          >
            Install updates
          </Button>
        </div>
        {
          error && (
            <div className="update-error">
              {error}
            </div>
          )
        }
      </>
    );
  }
  return null;
}

AutoUpdateButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

AutoUpdateButton.defaultProps = {
  className: undefined,
  style: undefined,
};

export default AutoUpdateButton;
