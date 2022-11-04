/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Modal, Icon} from 'antd';
import styles from './ModalConfirm.css';

function ModalConfirm (
  {
    onOk,
    onCancel,
    okText = 'OK',
    cancelText = 'CANCEL',
    width = 450,
    visible,
    footer,
    title,
    children
  }
) {
  return (
    <Modal
      onOk={onOk}
      onCancel={onCancel}
      okText={okText}
      cancelText={cancelText}
      width={width}
      visible={visible}
      closable={false}
      bodyStyle={{
        padding: '30px 40px 10px 40px'
      }}
      footer={footer}
      wrapClassName={styles.modalWrapper}
    >
      <div className={styles.container}>
        <p className={styles.title}>
          <Icon
            type="question-circle"
            className="cp-alert-color"
            style={{
              marginRight: 16,
              fontSize: 'x-large',
              float: 'left'
            }}
          />
          <b style={{fontSize: 'larger'}}>
            {title}
          </b>
        </p>
        {children}
      </div>
    </Modal>
  );
}

ModalConfirm.propTypes = {
  onOk: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
  title: PropTypes.string.isRequired,
  visible: PropTypes.bool.isRequired,
  okText: PropTypes.string,
  cancelText: PropTypes.string,
  width: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  footer: PropTypes.node,
  children: PropTypes.node
};

export default ModalConfirm;
