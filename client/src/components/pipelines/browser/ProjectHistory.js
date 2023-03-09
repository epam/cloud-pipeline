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
import folders from '../../../models/folders/Folders';
import pipelines from '../../../models/pipelines/Pipelines';
import RunTable from '../../runs/run-table';
import LoadingView from '../../special/LoadingView';
import EditableField from '../../special/EditableField';
import {Alert, Icon, Row} from 'antd';
import connect from '../../../utils/connect';
import HiddenObjects from '../../../utils/hidden-objects';
import styles from './Browser.css';

@connect({
  folders,
  pipelines
})
@HiddenObjects.checkFolders(p => (p.params ? p.params.id : p.id))
@inject(({folders}, {params}) => {
  return {
    folder: folders.load(params.id),
    folderId: params.id ? Number(params.id) : undefined,
    id: params.id,
    folders,
    pipelines
  };
})
@observer
export default class ProjectHistory extends React.Component {
  render () {
    if (this.props.folder.pending && !this.props.folder.loaded) {
      return <LoadingView />;
    }
    if (!this.props.folder.pending && this.props.folder.error) {
      return <Alert message={this.props.folder.error} type="error" />;
    }
    const folderTitleClassName = this.props.folder.value.locked ? styles.readonly : undefined;
    const {
      folderId
    } = this.props;
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <table style={{width: '100%'}}>
          <tbody>
            <tr>
              <td>
                <Row type="flex" className={styles.itemHeader} align="middle">
                  <Icon
                    type="clock-circle-o"
                    className={`${styles.editableControl} ${folderTitleClassName}`}
                  />
                  {
                    this.props.folder.value.locked &&
                    <Icon
                      className={`${styles.editableControl} ${folderTitleClassName}`}
                      type="lock" />
                  }
                  <EditableField
                    readOnly
                    className={folderTitleClassName}
                    allowEpmty={false}
                    editStyle={{flex: 1}}
                    text={`${this.props.folder.value.name} runs history`} />
                </Row>
              </td>
              <td className={styles.currentFolderActions}>
                {'\u00A0'}
              </td>
            </tr>
          </tbody>
        </table>
        <RunTable
          filters={{
            projectIds: folderId ? [folderId] : undefined
          }}
          className={styles.runTable}
        />
      </div>
    );
  }
}
