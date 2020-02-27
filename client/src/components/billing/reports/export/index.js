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
import {Provider as MobxProvider} from 'mobx-react';
import {Button, Icon} from 'antd';
import ExportConsumer from './export-consumer';
import exportStore from './export-store';
import * as ExportComposers from './composers';

class ExportReports extends React.Component {
  static Provider = ({children}) => (
    <MobxProvider export={exportStore}>
      {children}
    </MobxProvider>
  );

  static Consumer = ExportConsumer;

  render () {
    const {className} = this.props;
    return (
      <Button
        id="export-reports"
        className={className}
        onClick={() => exportStore.doExport()}
      >
        <Icon type="export" />
        Export
      </Button>
    );
  }
}

ExportReports.propTypes = {
  className: PropTypes.string
};

export default ExportReports;
export {ExportComposers};
