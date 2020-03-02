/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer, Provider} from 'mobx-react';
import ImageGenerator from './image-generator';

class ExportImageConsumer extends React.Component {
  static uniqueIdentifier = 0;

  static Generator = (...opts) => inject('imageGenerator')(...opts);

  domIdentifier;

  imageGenerator;

  constructor (props) {
    super(props);
    ExportImageConsumer.uniqueIdentifier += 1;
    this.domIdentifier = `image-consumer-${ExportImageConsumer.uniqueIdentifier}`;
    this.imageGenerator = new ImageGenerator(this.domIdentifier);
  }

  componentDidMount () {
    const {export: exportStore} = this.props;
    exportStore.attachImage(this);
  }

  componentWillUnmount () {
    const {export: exportStore} = this.props;
    exportStore.detachImage(this);
  }

  getExportData = () => {
    return this.imageGenerator.generate();
  };

  render () {
    const {className, children, style} = this.props;
    return (
      <Provider imageGenerator={this.imageGenerator}>
        <div
          id={this.domIdentifier}
          className={className}
          style={style}
        >
          {children}
        </div>
      </Provider>
    );
  }
}

ExportImageConsumer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  order: PropTypes.number
};

ExportImageConsumer.defaultProps = {
  order: 0
};

export default inject('export')(
  observer(ExportImageConsumer)
);
