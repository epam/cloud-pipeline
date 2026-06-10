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
import {inject, observer} from 'mobx-react';
import {Link} from 'react-router-dom';
import {Row} from 'antd';
import RunTable, {Columns} from '../../../runs/run-table';
import parseQueryParameters from '../../../../utils/queryParameters';
import styles from './PipelineHistory.module.css';

@inject(({routing}) => {
  const queryParameters = parseQueryParameters(routing);
  const {params} = routing;
  const allVersions = Object.hasOwn(queryParameters, 'all')
    ? queryParameters.all === undefined
      ? true
      : queryParameters.all === 'true'
    : false;

  return {
    allVersions,
    pipelineId: params.id,
    version: params.version,
  };
})
@observer
export default class PipelineHistory extends React.Component {
  render() {
    const {pipelineId, version, allVersions} = this.props;
    const beforeTable = () => {
      if (allVersions) {
        const currentVersionLink = `/${pipelineId}/${version}/history`;
        return (
          <Row style={{marginBottom: 5, padding: 2}}>
            {}
            Currently viewing history for <b>all versions</b>.{' '}
            <Link to={currentVersionLink}>
              View only current version (<b>{version}</b>) history
            </Link>
          </Row>
        );
      } else {
        const allVersionsLink = `/${pipelineId}/${version}/history?all`;
        return (
          <Row style={{marginBottom: 5, padding: 2}}>
            {}
            Currently viewing history for <b>{version}</b> version.{' '}
            <Link to={allVersionsLink}>View all versions history</Link>
          </Row>
        );
      }
    };
    return (
      <div className={styles.container} style={{overflowY: 'auto'}}>
        <RunTable
          className={styles.runTable}
          filters={{
            pipelineIds: [pipelineId],
            versions: allVersions ? undefined : [version],
          }}
          disableFilters={[Columns.pipeline]}
          beforeTable={beforeTable}
        />
      </div>
    );
  }
}
