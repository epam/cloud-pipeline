import React from 'react';
import {Button} from 'antd';
import checker from '../../../auto-update';
import './configuration.css';

export default class UpdateButton extends React.Component {
  state = {
    availableForOS: false,
    available: false,
    checking: true,
    updating: false,
    error: undefined,
    isLatest: false
  };

  componentDidMount () {
    checker.addEventListener(this.checkCallback);
  }

  checkCallback = (result = {}) => {
    const {
      available = false,
      isLatest,
      availableForOS
    } = result;
    this.setState({
      available,
      checking: false,
      availableForOS,
      isLatest
    });
  };

  checkForUpdates = () => {
    this.setState({
      checking: true,
      error: undefined
    }, () => checker.checkForUpdates());
  };

  installUpdates = () => {
    this.setState({
      updating: true,
      available: false,
      checking: false,
      error: undefined
    }, async () => {
      try {
        await checker.update();
        // Normally, update script should kill current process.
        // If for some reason it is finished, but current process is alive,
        // we need to warn user that something went wrong.
        throw new Error(`Unknown error updating and re-launching ${checker.appName}.`);
      } catch (e) {
        console.log(e.message);
        this.setState({
          error: e.message,
          updating: false
        });
      }
    });
  };

  render () {
    const {
      checking,
      updating,
      available,
      availableForOS,
      isLatest,
      error
    } = this.state;
    if (!availableForOS) {
      return null;
    }
    if (updating) {
      return (
        <div className="auto-update-row">
          Updating... do not close the application
        </div>
      );
    }
    if (checking) {
      return (
        <div className="auto-update-row">
          Checking for updates...
        </div>
      );
    }
    if (available) {
      return (
        <div className="auto-update-row">
          <span style={{marginRight: 5}}>
            New version of <b>{checker.appName}</b> is available.
          </span>
          <Button
            type="primary"
            onClick={this.installUpdates}
          >
            Install updates
          </Button>
        </div>
      );
    }
    return (
      <div>
        <div className="auto-update-row">
          {
            isLatest && (
              <span style={{marginRight: 5}}>
              Current version is up-to-date.
            </span>
            )
          }
          <Button
            type="primary"
            onClick={this.checkForUpdates}
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
      </div>
    );
  }
}
