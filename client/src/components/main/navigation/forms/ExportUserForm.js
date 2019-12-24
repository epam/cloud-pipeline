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
import {
  Checkbox,
  Modal,
  Button,
  message
} from 'antd';
import FileSaver from 'file-saver';
import ExportUsers from '../../../../models/user/Export';
import styles from './ExportUserForm.css';

const Keys = [
  {label: 'Header', value: 'includeHeader'},
  {label: 'User identifier', value: 'includeId'},
  {label: 'User name', value: 'includeUserName'},
  {label: 'User email', value: 'includeEmail'},
  {label: 'User first login date', value: 'includeFirstLoginDate'},
  {label: 'User registration time', value: 'includeRegistrationDate'},
  {label: 'User metadata', value: 'includeMetadata'},
  {label: 'User groups', value: 'includeGroups'},
  {label: 'User roles', value: 'includeRoles'}
];

const DefaultValues = Keys.map(k => k.value);

async function doExport (fields = DefaultValues) {
  try {
    const request = new ExportUsers();
    const payload = Keys
      .map(({value}) => ({[value]: fields.indexOf(value) >= 0}))
      .reduce((r, c) => ({...r, ...c}), {});
    await request.send(payload);
    if (request.value instanceof Blob) {
      FileSaver.saveAs(request.value, 'export.csv');
    } else if (request.error) {
      message.error(request.error, 5);
    } else {
      message.error('Error exporting users', 5);
    }
  } catch (e) {
    message.error(`Error exporting users: ${e.toString()}`, 5);
  }
}

export {doExport, DefaultValues, Keys};

export default function ExportUserForm ({
  onCancel,
  onChange,
  onSubmit,
  values,
  visible
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
      onClick={() => onSubmit(values)}
    >
      Download CSV
    </Button>
  ];

  return (
    <Modal
      title="CSV fields"
      visible={visible}
      footer={modalControls}
      onCancel={onCancel}
    >
      <Checkbox.Group
        className={styles.inputContainer}
        options={Keys}
        value={values}
        onChange={(checkedValues) => onChange(checkedValues)}
      />
    </Modal>
  );
}

ExportUserForm.propTypes = {
  onCancel: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  values: PropTypes.array
};
