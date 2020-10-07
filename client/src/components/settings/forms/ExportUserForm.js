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
import {
  Collapse,
  Checkbox,
  Modal,
  Button,
  message
} from 'antd';
import FileSaver from 'file-saver';
import ExportUsers from '../../../models/user/Export';
import styles from './ExportUserForm.css';

const Keys = [
  {label: 'Identifier', value: 'includeId'},
  {label: 'User name', value: 'includeUserName'},
  {label: 'Attributes', value: 'includeAttributes'},
  {label: 'Registration date', value: 'includeRegistrationDate'},
  {label: 'First login date', value: 'includeFirstLoginDate'},
  {label: 'Roles', value: 'includeRoles'},
  {label: 'Groups', value: 'includeGroups'},
  {label: 'Blocked', value: 'includeStatus'},
  {label: 'Default data storage', value: 'includeDataStorage'},
  {label: 'Header', value: 'includeHeader'}
];

const DefaultValues = Keys.map(k => k.value);

async function doExport (fields = DefaultValues, metadataKeys = []) {
  try {
    const request = new ExportUsers();
    const payload = Keys
      .map(({value}) => ({[value]: fields.indexOf(value) >= 0}))
      .reduce((r, c) => ({...r, ...c}), {});
    if (metadataKeys.length > 0) {
      payload.metadataColumns = metadataKeys.slice();
    }
    await request.send(payload);
    if (request.value instanceof Blob) {
      const error = await checkBlob(request.value);
      if (error) {
        message.error(error, 5);
      } else {
        FileSaver.saveAs(request.value, 'export.csv');
      }
    } else if (request.error) {
      message.error(request.error, 5);
    } else {
      message.error('Error exporting users', 5);
    }
  } catch (e) {
    message.error(`Error exporting users: ${e.toString()}`, 5);
  }
}

function checkBlob (blob) {
  return new Promise(resolve => {
    const fr = new FileReader();
    fr.onload = function () {
      try {
        const {status, message} = JSON.parse(this.result);
        if (/^error$/i.test(status)) {
          resolve(message || 'Error exporting users');
        } else {
          resolve(null);
        }
      } catch (_) {}
      resolve(null);
    };
    fr.readAsText(blob);
  });
}

export {doExport, DefaultValues, Keys};

export default function ExportUserForm ({
  onCancel,
  onChange,
  onSubmit,
  values,
  visible,
  metadataKeys,
  selectedMetadataKeys
}) {
  const modalControls = [
    <Button
      key="back"
      onClick={onCancel}
    >
      Cancel
    </Button>,
    <Button
      disabled={values.length === 0}
      key="submit"
      type="primary"
      onClick={() => {
        onSubmit && onSubmit(values, selectedMetadataKeys);
        onCancel && onCancel();
      }}
    >
      Download CSV
    </Button>
  ];

  let modalContent;

  if (metadataKeys.length > 0) {
    modalContent = (
      <Collapse defaultActiveKey={['fields']}>
        <Collapse.Panel key="fields" header="Fields">
          <Checkbox.Group
            className={styles.inputContainer}
            options={Keys}
            value={values}
            onChange={(checkedValues) => onChange(checkedValues, selectedMetadataKeys)}
          />
        </Collapse.Panel>
        <Collapse.Panel key="attributes" header="Attributes">
          <Checkbox.Group
            className={styles.inputContainer}
            options={metadataKeys}
            value={selectedMetadataKeys}
            onChange={(checkedValues) => onChange(values, checkedValues)}
          />
        </Collapse.Panel>
      </Collapse>
    );
  } else {
    modalContent = (
      <Checkbox.Group
        className={styles.inputContainer}
        options={Keys}
        value={values}
        onChange={(checkedValues) => onChange(checkedValues, selectedMetadataKeys)}
      />
    );
  }

  return (
    <Modal
      title="CSV fields"
      visible={visible}
      footer={modalControls}
      onCancel={onCancel}
    >
      {modalContent}
    </Modal>
  );
}

ExportUserForm.propTypes = {
  onCancel: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  values: PropTypes.array,
  metadataKeys: PropTypes.array,
  selectedMetadataKeys: PropTypes.array
};
