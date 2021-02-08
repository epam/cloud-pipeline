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
import classNames from 'classnames';
import CloudCredentialsForm from './cloud-credentials-form';
import styles from './cloud-provider.css';

class ProviderForm extends React.Component {
  render () {
    const {className, provider} = this.props;
    return (
      <div
        className={
          classNames(
            styles.container,
            className
          )
        }
      >
        <CloudCredentialsForm provider={provider} />
      </div>
    );
  }
}

ProviderForm.propTypes = {
  className: PropTypes.string,
  provider: PropTypes.string
};

export default ProviderForm;
