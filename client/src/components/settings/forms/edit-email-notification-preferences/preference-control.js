/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import PreferenceLoad from '../../../../models/preferences/PreferenceLoad';
import {Preferences} from './configuration';
import {Input, InputNumber} from 'antd';
import styles from './preference-control.css';

function wrapValue (value) {
  if (value === undefined || value === null) {
    return null;
  }
  return `${value}`;
}

class PreferenceControl extends React.Component {
  state = {
    value: undefined,
    initialValue: undefined,
    error: undefined,
    pending: true,
    meta: {}
  };

  componentDidMount () {
    this.updateValues();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.session !== this.props.session ||
      prevProps.preference !== this.props.preference
    ) {
      this.updateValues();
    }
  }

  updateValues = () => {
    const {preference: preferenceName} = this.props;
    const request = new PreferenceLoad(preferenceName);
    this.setState({
      pending: true
    }, () => {
      request
        .fetch()
        .then(() => {
          if (request.loaded) {
            const preference = request.value;
            if (preference) {
              this.setState({
                error: undefined,
                value: wrapValue(preference.value),
                initialValue: wrapValue(preference.value),
                pending: false,
                meta: {...preference}
              }, this.onChange);
            } else {
              this.setState({
                error: `Preference ${preferenceName} not found`,
                pending: false,
                meta: {}
              }, this.onChange);
            }
          } else if (request.error) {
            this.setState({
              error: request.error,
              pending: false,
              meta: {}
            }, this.onChange);
          }
        })
        .catch((e) => {
          this.setState({
            error: e.toString(),
            pending: false,
            meta: {}
          }, this.onChange);
        });
    });
  };

  onChange = () => {
    const {onChange} = this.props;
    if (onChange) {
      const {value, initialValue, meta} = this.state;
      onChange(value, initialValue !== value, meta);
    }
  };

  renderStringControl = (preference) => {
    if (!preference) {
      return null;
    }
    const {value, pending} = this.state;
    const onValueChange = (e) => {
      this.setState({
        value: wrapValue(e.target.value)
      }, this.onChange);
    };
    return (
      <div className={styles.controlRow}>
        <span className={styles.label}>
          {preference.name}
        </span>
        <Input
          disabled={pending}
          className={styles.control}
          value={value}
          onChange={onValueChange}
        />
      </div>
    );
  };

  renderNumberControl = (preference) => {
    if (!preference) {
      return null;
    }
    const {value, pending} = this.state;
    const onValueChange = (e) => {
      this.setState({
        value: wrapValue(e)
      }, this.onChange);
    };
    return (
      <div className={styles.controlRow}>
        <span className={styles.label}>
          {preference.name}
        </span>
        <InputNumber
          disabled={pending}
          className={styles.control}
          value={Number.isNaN(Number(value)) ? 0 : Number(value)}
          min={preference.min}
          max={preference.max}
          onChange={onValueChange}
        />
      </div>
    );
  };

  render () {
    const {error} = this.state;
    const {preference: preferenceName} = this.props;
    const preference = Preferences.find(p => p.preference === preferenceName);
    let control;
    if (preference) {
      switch (preference.type) {
        case 'number': control = this.renderNumberControl(preference); break;
        case 'string':
        default:
          control = this.renderStringControl(preference); break;
      }
    }
    return (
      <div>
        <div>
          {control}
        </div>
        {
          error && (
            <div className={styles.error}>
              {error}
            </div>
          )
        }
      </div>
    );
  }
}

PreferenceControl.propTypes = {
  preference: PropTypes.string,
  onChange: PropTypes.func,
  session: PropTypes.number
};

export default PreferenceControl;
