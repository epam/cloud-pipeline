/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Input, Modal, Alert} from 'antd';

class SystemJobParameters extends React.Component {
  state = {
    parameters: undefined
  }

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.job !== this.props.job ||
      prevProps.visible !== this.props.visible
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    this.setState({
      parameters: undefined
    });
  };

  onChangeParameters = (event) => {
    this.setState({
      parameters: event.target.value
    });
  };

  onLaunch = () => {
    const {
      job,
      onLaunch
    } = this.props;
    const {
      parameters
    } = this.state;
    if (typeof onLaunch === 'function') {
      onLaunch(job, parameters);
    }
  };

  renderAlerts = () => {
    const {parametersFromScript} = this.props;
    if (!parametersFromScript) {
      return null;
    }
    const alerts = (parametersFromScript.parameters || [])
      .filter(parameter => parameter.description);
    return alerts.length > 0 ? (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        {
          alerts.map(alert => (
            <Alert
              key={alert.parameterId}
              message={(
                <p>
                  <b>{alert.parameterId}: </b>
                  <span>
                    {alert.description}
                  </span>
                </p>
              )}
              type="info"
              style={{marginBottom: '3px'}}
            />
          ))}
      </div>
    ) : null;
  };

  render () {
    const {
      className,
      style,
      visible,
      onCancel
    } = this.props;
    const {
      parameters
    } = this.state;
    return (
      <Modal
        className={className}
        style={style}
        title="Job parameters"
        onCancel={onCancel}
        visible={visible}
        footer={(
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between'
            }}
          >
            <Button
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              disabled={!parameters || parameters.length === 0}
              onClick={this.onLaunch}
            >
              LAUNCH
            </Button>
          </div>
        )}
      >
        {this.renderAlerts()}
        <Input
          style={{width: '100%'}}
          value={parameters}
          placeholder="Specify job parameters"
          onChange={this.onChangeParameters}
        />
      </Modal>
    );
  }
}

SystemJobParameters.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  job: PropTypes.object,
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  onLaunch: PropTypes.func,
  parametersFromScript: PropTypes.object
};

export default SystemJobParameters;
