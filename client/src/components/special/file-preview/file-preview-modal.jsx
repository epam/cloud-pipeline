import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Modal} from 'antd';
import {FilePreview} from './file-preview';
import styles from './file-preview.css';

export const FilePreviewModal = (
  {
    className,
    style,
    filePath,
    header,
    footer,
    visible,
    ...modalProps
  }
) => {
  return (
    <Modal
      className={classNames(className, styles.filePreviewModal)}
      style={style}
      bodyStyle={{
        maxHeight: 'calc(80vh - 200px)',
        overflow: 'auto',
        display: 'flex',
        flexDirection: 'column'
      }}
      width="80%"
      {...modalProps}
      visible={visible}
      footer={false}
    >
      {
        visible && (
          <FilePreview
            filePath={filePath}
            style={{width: '100%', flex: '1', overflow: 'auto'}}
            header={header}
            footer={footer}
          />
        )
      }
    </Modal>
  );
};

FilePreviewModal.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filePath: PropTypes.string,
  title: PropTypes.node,
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  maskClosable: PropTypes.bool,
  footer: PropTypes.node,
  header: PropTypes.node
};
