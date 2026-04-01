import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './upload-files-list.css';
import UploadFileStatus from './upload-file-status';

class UploadFilesList extends React.Component {
  render () {
    const {
      className,
      style,
      session
    } = this.props;
    return (
      <div
        className={classNames(className, styles.uploadFilesList)}
        style={style}
      >
        {session.files.map((fileUpload, idx) => (
          <UploadFileStatus file={fileUpload} key={`file-upload-${fileUpload.file.name}-${idx}`} />
        ))}
      </div>
    );
  }
}

UploadFilesList.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  session: PropTypes.object // FileUploadList instance
};

export default UploadFilesList;
