import React from 'react';
import PropTypes from 'prop-types';
import {
  inject,
  observer} from 'mobx-react';
import classNames from 'classnames';
import { CloseOutlined, FileOutlined } from '@ant-design/icons';
import displaySize from '../../../../../utils/displaySize';
import styles from './upload-files-area.css';

@inject('uiLaunchParametersConfiguration')
@observer
class UploadFilesArea extends React.Component {
  state = {
    isDragging: false
  };

  fileInputRef;

  initializeFileInput = (fileInputRef) => {
    this.fileInputRef = fileInputRef;
  };

  handleDragOver = (event) => {
    const {isDragging} = this.state;
    if (event.dataTransfer && event.dataTransfer.files) {
      const {multiple} = this.props;
      if (multiple || event.dataTransfer.files.length === 1) {
        event.preventDefault();
        event.stopPropagation();
        if (!isDragging) {
          this.setState({isDragging: true});
        }
      }
    }
  };

  handleDragLeave = (event) => {
    const {isDragging} = this.state;
    event.preventDefault();
    event.stopPropagation();
    if (isDragging) {
      this.setState({isDragging: false});
    }
  };

  handleDrop = (event) => {
    event.preventDefault();
    event.stopPropagation();
    const files = Array.from(event.dataTransfer.files);
    this.setState({
      isDragging: false
    });
    this.appendFiles(files);
  };

  handleFileSelect = (event) => {
    const addedFiles = Array.from(event.target.files);
    event.target.value = '';
    this.appendFiles(addedFiles);
  };

  appendFiles = (addedFiles) => {
    const {onFilesChange, files = []} = this.props;
    if (onFilesChange) {
      onFilesChange([...files, ...addedFiles]);
    }
  };

  onRemoveFile = (file) => {
    const {files = [], onFilesChange} = this.props;
    if (onFilesChange) {
      const idx = files.indexOf(file);
      if (idx >= 0) {
        const newFilesList = files.slice();
        newFilesList.splice(idx, 1);
        onFilesChange(newFilesList);
      }
    }
  };

  onUploadFilesClick = () => {
    if (this.fileInputRef) {
      this.fileInputRef.click();
    }
  };

  onClearListClicked = () => {
    const {onFilesChange} = this.props;
    if (onFilesChange) {
      onFilesChange([]);
    }
  }

  render () {
    const {
      className,
      style,
      files = [],
      multiple
    } = this.props;
    const {
      isDragging
    } = this.state;
    return (
      <div
        className={classNames(className, styles.uploadFilesArea, {
          [styles.dragging]: isDragging
        })}
        style={style}
        onDragOver={this.handleDragOver}
        onDragLeave={this.handleDragLeave}
        onDrop={this.handleDrop}
      >
        <input
          type="file"
          ref={this.initializeFileInput}
          multiple={multiple}
          onChange={this.handleFileSelect}
          style={{display: 'none'}}
        />
        <div className={classNames(styles.uploadFilesAreaDnd, 'cp-bordered')} />
        <div className={classNames(styles.uploadFilesHeader, {[styles.empty]: files.length === 0})}>
          <span>
            <a onClick={this.onUploadFilesClick}>Upload local files</a> or Drag & Drop files here
          </span>
        </div>
        <div className={styles.uploadFilesList}>
          {files.map((f, idx) => (
            <div key={`file-${f.name}-${idx}`} className={styles.uploadedFile}>
              <FileOutlined />
              <span className={styles.uploadedFileName}>{f.name}</span>
              <span className={styles.uploadedFileSize}>({displaySize(f.size)})</span>
              <CloseOutlined style={{marginLeft: 5}}
                className={classNames('cp-error', styles.uploadedFileAction)}
                onClick={() => this.onRemoveFile(f)} />
            </div>
          ))}
        </div>
        {
          files.length > 0 && (
            <div className={styles.uploadFilesFooter}>
              <a onClick={this.onClearListClicked}>Clear list</a>
            </div>
          )
        }
      </div>
    );
  }
}

UploadFilesArea.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  files: PropTypes.array,
  onFilesChange: PropTypes.func,
  multiple: PropTypes.bool
};

UploadFilesArea.defaultProps = {
  multiple: true
};

export default UploadFilesArea;
