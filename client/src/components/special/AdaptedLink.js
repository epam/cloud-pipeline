/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, {Component} from 'react';
import {Link} from 'react-router';
import PropTypes from 'prop-types';

export default class AdaptedLink extends Component {
  static propTypes = {
    to: PropTypes.string,
    children: PropTypes.any.isRequired,
    location: PropTypes.object,
    id: PropTypes.string
  };

  render () {
    const {to, children, location: {pathinfo}} = this.props;
    const additionalProps = {};
    if (this.props.id) {
      additionalProps.id = this.props.id;
    }
    return (
      to === pathinfo
        ? <span {...additionalProps}>{children}</span>
        : <Link {...additionalProps} to={to} onClick={(e) => e.stopPropagation()}>{children}</Link>
    );
  }
}
