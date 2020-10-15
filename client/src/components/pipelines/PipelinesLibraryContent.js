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

import React from 'react';
import PropTypes from 'prop-types';
import {Card} from 'antd';
import styles from './PipelinesLibrary.css';

export default class PipelinesLibraryContent extends React.Component {

  static propTypes = {
    onReloadTree: PropTypes.func,
    location: PropTypes.string,
    style: PropTypes.object
  };

  shouldComponentUpdate (nextProps) {
    return nextProps.location !== this.props.location;
  }

  render () {
    return (
      <Card
        id="pipelines-library-content"
        className={styles.libraryCard}
        bodyStyle={
          Object.assign(
            {
              padding: 5,
              height: '99%',
              display: 'flex',
              flexDirection: 'column',
              flex: 1
            },
            this.props.style || {}
          )
        }
      >
        {
          React.Children.map(
            this.props.children,
            (child) => React.cloneElement(
              child, {
                onReloadTree: this.props.onReloadTree,
                browserLocation: this.props.location
              }
            )
          )
        }
      </Card>
    );
  }
}
