/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Icon, Tooltip} from 'antd';
import pipelineRunSSHCache from '../../../../../models/pipelines/PipelineRunSSHCache';
import MultizoneUrl from '../../../../special/multizone-url';

const MOUSE_ENTER_DELAY_MS = 500;

class RunSSHButton extends React.Component {
  state = {
    pending: false,
    sshConfiguration: undefined,
    error: undefined,
    defaultNavigationRequested: false,
    visible: false
  };

  onClick = () => {
    this.setState({
      defaultNavigationRequested: true
    }, () => this.loadRunSSHConfiguration());
  };

  clearLoadRunSSHConfigurationTimer = () => {
    if (this.loadRunSSHConfigurationTimer) {
      clearTimeout(this.loadRunSSHConfigurationTimer);
      this.loadRunSSHConfigurationTimer = undefined;
    }
  };

  loadRunSSHConfiguration = () => {
    this.clearLoadRunSSHConfigurationTimer();
    const {
      pending,
      sshConfiguration,
      error
    } = this.state;
    if (!error && !sshConfiguration && !pending) {
      this.setState({pending: true}, () => {
        const runSSH = pipelineRunSSHCache.getPipelineRunSSH(this.props.runId);
        runSSH.fetchIfNeededOrWait()
          .then(() => {
            if (runSSH.loaded) {
              const configuration = runSSH.value || {};
              return Promise.resolve({...configuration});
            } else {
              throw new Error(runSSH.error || 'Error fetching SSH endpoint');
            }
          })
          .then((configuration) => {
            return new Promise((resolve) => {
              this.setState({
                sshConfiguration: {...configuration}
              }, () => {
                const {defaultNavigationRequested} = this.state;
                if (defaultNavigationRequested) {
                  if (Object.values(configuration).length === 1) {
                    window.open(Object.values(configuration).pop(), '_blank').focus();
                  } else {
                    this.setState({visible: true});
                  }
                }
                resolve();
              });
            });
          })
          .catch((e) => {
            this.setState({error: e.message});
          })
          .then(() => {
            this.setState({pending: false});
          });
      });
    }
  };

  onMouseEnter = () => {
    this.clearLoadRunSSHConfigurationTimer();
    this.loadRunSSHConfigurationTimer = setTimeout(
      this.loadRunSSHConfiguration,
      MOUSE_ENTER_DELAY_MS
    );
  };

  onMouseLeave = () => {
    this.clearLoadRunSSHConfigurationTimer();
  };

  render () {
    const {
      pending,
      error,
      sshConfiguration
    } = this.state;
    const {
      visibilityChanged,
      style,
      className,
      icon
    } = this.props;
    if (error) {
      return (
        <Tooltip
          title={error}
        >
          <div
            className={className}
            style={style}
          >
            {
              icon && (<Icon type={icon} />)
            }
            SSH
            <Icon type="exclamation-circle-o" />
          </div>
        </Tooltip>
      );
    }
    if (sshConfiguration) {
      return (
        <MultizoneUrl
          className={className}
          style={style}
          configuration={sshConfiguration}
          getPopupContainer={() => document.getElementById('root')}
          visibilityChanged={visibilityChanged}
        >
          {
            icon && (<Icon type={icon} />)
          }
          <span>SSH</span>
        </MultizoneUrl>
      );
    }
    return (
      <div
        className={className}
        style={style}
        onClick={this.onClick}
        onMouseEnter={this.onMouseEnter}
        onMouseLeave={this.clearLoadRunSSHConfigurationTimer}
      >
        {
          icon && (<Icon type={icon} />)
        }
        <span>SSH</span>
        {
          pending && (
            <Icon
              type="loading"
              style={{marginLeft: 5}}
            />
          )
        }
      </div>
    );
  }
}

RunSSHButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  visibilityChanged: PropTypes.func,
  icon: PropTypes.string
};

export default RunSSHButton;
