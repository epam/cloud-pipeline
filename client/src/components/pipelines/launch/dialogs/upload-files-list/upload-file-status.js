import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Progress} from 'antd';
import { CheckCircleFilled, ExclamationCircleFilled, LoadingOutlined } from '@ant-design/icons';
import styles from './upload-files-list.css';
import displaySize from '../../../../../utils/displaySize';

class UploadFileStatus extends React.PureComponent {
  state = {
    status: undefined
  };

  componentDidMount () {
    this.addFileListeners();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.file !== this.props.file) {
      this.removeFileListeners(prevProps.file);
      this.addFileListeners(this.props.file);
    }
  }

  componentWillUnmount () {
    this.removeFileListeners();
  }

  addFileListeners = (file = this.props.file) => {
    if (file) {
      file.addEventListener(this.listener);
      this.setState({status: file.getState()});
    }
  };

  removeFileListeners = (file = this.props.file) => {
    if (file) {
      file.removeEventListener(this.listener);
    }
  }

  listener = (fileStatus) => {
    this.setState({
      status: fileStatus
    });
  };

  render () {
    const {
      className,
      style,
      file
    } = this.props;
    if (!file) {
      return null;
    }
    const {
      status
    } = this.state;
    const {
      done = false,
      indeterminate = true,
      progress = 0.0,
      error = undefined
    } = status || {};
    const statusIcon = (() => {
      if (error) {
        return <ExclamationCircleFilled className="cp-error" />;
      }
      if (!done) {
        return <LoadingOutlined />;
      }
      return <CheckCircleFilled className="cp-success" />;
    })();
    return (
      <div
        className={classNames(className, styles.uploadFile)}
        style={style}
      >
        <div className={styles.uploadFileStatus}>
          {statusIcon}
          <span className={styles.uploadFileName}>{file.name}</span>
          <span className={styles.uploadFileSize}>({displaySize(file.size)})</span>
        </div>
        {
          !error && !indeterminate && progress < 100 && (
            <div>
              <Progress percent={Math.round(progress)} showInfo strokeWidth={2} />
            </div>
          )
        }
        {
          error && (
            <div className={classNames(styles.uploadFileError, 'cp-error')}>
              {error}
            </div>
          )
        }
      </div>
    );
  }
}

UploadFileStatus.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  file: PropTypes.object // FileUpload instance
};

export default UploadFileStatus;
