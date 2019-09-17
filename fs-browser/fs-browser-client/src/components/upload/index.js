import React from 'react';
import PropTypes from 'prop-types';
import {inject} from 'mobx-react';
import classNames from 'classnames';
import UploadButton from './upload-button';
import styles from './upload.css';

@inject('messages', 'taskManager')
class Upload extends React.Component {
  static propTypes = {
    path: PropTypes.string.isRequired,
    showButton: PropTypes.bool,
    showUploadArea: PropTypes.bool,
    uploadAreaClassName: PropTypes.string,
  };

  static defaultProps = {
    showButton: false,
    showUploadArea: true,
    uploadAreaClassName: null,
  };

  state = {
    drop: null,
  };

  onFilesChanged = async (files) => {
    if (!files || !files.length) {
      return;
    }
    const {messages, path, taskManager} = this.props;
    const promises = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      promises.push(taskManager.upload(`${path}/${file.name}`, path, file));
    }
    const errors = await Promise.all(promises);
    if (errors.filter(Boolean).length) {
      messages.error(errors.filter(Boolean).join('\n'), 5);
    }
  };

  onDragLeave = () => {
    this.setState({drop: null});
  };

  onDragOver = (event) => {
    const {drop} = this.state;
    if (drop !== event) {
      this.setState({drop: event});
    }
    event.preventDefault();
    event.stopPropagation();
  };

  onDrop = (event) => {
    event.preventDefault();
    event.stopPropagation();
    this.setState({drop: null});
    if (event.dataTransfer && event.dataTransfer.files) {
      this.onFilesChanged(event.dataTransfer.files);
    }
  };

  render() {
    const {
      children,
      showButton,
      showUploadArea,
      uploadAreaClassName,
    } = this.props;
    const {drop} = this.state;
    const Wrapper = ({content}) => {
      if (showUploadArea) {
        return (
          <div
            className={
              classNames(
                styles.container,
                {
                  [styles.drop]: !!drop,
                }
              )
            }
            onDragLeave={this.onDragLeave}
            onDragOver={this.onDragOver}
            onDrop={this.onDrop}
          >
            {content}
          </div>
        );
      }
      return (
        <div
          className={styles.container}
        >
          {content}
        </div>
      );
    };
    return (
      <Wrapper
        content={(
          <div className={classNames(styles.uploadArea, uploadAreaClassName)}>
            {children}
            {showButton && (
              <UploadButton
                className={styles.uploadButton}
                onUpload={this.onFilesChanged}
              />
            )}
          </div>
        )}
      />
    );
  }
}

export default Upload;
