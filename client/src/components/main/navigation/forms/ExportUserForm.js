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
  Button
} from 'antd';
import styles from './ExportUserForm.css';

export default function ExportUserForm ({
  onCancel,
  onChange,
  onSubmit,
  exportUsersOptions,
  exportUsersDefault
}) {
  const modalControls = [
    <Button
      key="back"
      onClick={onCancel}
    >
      Cancel
    </Button>,
    <Button
      key="submit"
      type="primary"
      onClick={(e) => onSubmit(e)}
    >
      Download CSV
    </Button>
  ];

  return (
    <Modal
      title="Please choose what data should be exported as CSV:"
      visible
      footer={modalControls}
    >
      <Checkbox.Group
        className={styles.inputContainer}
        options={exportUsersOptions}
        defaultValue={exportUsersDefault}
        onChange={(checkedValues) => onChange(checkedValues)}
      />
    </Modal>
  );
}

ExportUserForm.propTypes = {
  onCancel: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  exportUsersDefault: PropTypes.array,
  exportUsersOptions: PropTypes.array
};
