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
import {
  NotificationPreferences,
  PreferencesSectionTitle
} from './configuration';
import PreferenceControl from './preference-control';
import styles from './preference-control.css';

class NotificationPreferencesControl extends React.Component {
  state = {
    values: {}
  };

  componentDidMount () {
    this.update();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.session !== prevProps.session ||
      this.props.type !== prevProps.type
    ) {
      this.update();
    }
  }

  update = () => {
    this.setState({
      values: {}
    }, this.onChange);
  };

  onChange = () => {
    const modified = Object.values(this.state.values).some(v => v.modified);
    const {onChange} = this.props;
    if (onChange) {
      onChange(Object.values(this.state.values).map(v => v.value), modified);
    }
  };

  onPreferenceChanged = (preferenceName) => (value, modified, meta) => {
    const {values} = this.state;
    values[preferenceName] = {
      name: preferenceName,
      value: {
        ...(meta || {}),
        value
      },
      modified
    };
    this.setState({values}, this.onChange);
  };

  render () {
    const {type, session} = this.props;
    const title = PreferencesSectionTitle[type];
    const preferences = NotificationPreferences[type] || [];
    if (!preferences || !preferences.length) {
      return null;
    }
    return (
      <div className={styles.container}>
        {
          title && (
            <div className={styles.title}>
              {title}
            </div>
          )
        }
        {
          preferences.map((preference) => (
            <PreferenceControl
              key={preference}
              session={session}
              onChange={this.onPreferenceChanged(preference)}
              preference={preference}
            />
          ))
        }
      </div>
    );
  }
}

NotificationPreferencesControl.propTypes = {
  type: PropTypes.string,
  onChange: PropTypes.func,
  session: PropTypes.number
};

export default NotificationPreferencesControl;
