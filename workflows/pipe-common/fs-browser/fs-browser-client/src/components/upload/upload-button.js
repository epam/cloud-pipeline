import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './upload-button.css';

export default class UploadButton extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    onUpload: PropTypes.func,
  };

  static defaultProps = {
    className: null,
    onUpload: null,
  };

  onSelectFiles = () => {
    const {onUpload} = this.props;
    if (
      this.inputControl
      && this.inputControl.files
      && this.inputControl.files.length > 0
      && onUpload
    ) {
      onUpload(this.inputControl.files);
    }
  };

  onInitializeInput = (input) => {
    this.inputControl = input;
  };

  inputControl;

  render() {
    const {className, children} = this.props;
    return (
      <div
        className={
          classNames(className, styles.container)
        }
      >
        <input
          ref={this.onInitializeInput}
          className={styles.nativeInput}
          type="file"
          name="files"
          multiple
          onChange={this.onSelectFiles}
        />
        <button
          type="button"
          className={styles.button}
        >
          {children || 'Upload'}
        </button>
      </div>
    );
  }
}
