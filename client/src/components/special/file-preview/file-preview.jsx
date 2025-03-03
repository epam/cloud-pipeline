import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './file-preview.css';
import {Alert, Icon} from 'antd';
import {getFilePreviewConfiguration} from './utils';

class FilePreview extends React.Component {
  state = {
    preview: undefined,
    error: undefined,
    pending: true
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.filePath !== this.props.filePath) {
      this.updateFromProps();
    }
  }

  componentWillUnmount () {
    this.token = {};
  }

  updateFromProps = () => {
    const token = this.token = {};
    const commit = (fn) => {
      if (this.token === token) {
        fn();
      }
    };
    const {
      filePath
    } = this.props;
    (async () => {
      commit(() => {
        this.setState({
          pending: true,
          error: undefined,
          preview: undefined
        });
      });
      try {
        const preview = await getFilePreviewConfiguration(filePath);
        commit(() => {
          this.setState({
            pending: false,
            error: undefined,
            preview
          });
        });
      } catch (error) {
        commit(() => {
          this.setState({
            pending: false,
            error: error.message,
            preview: undefined
          });
        });
      }
    })();
  };

  renderContent = () => {
    const {
      pending,
      error,
      preview
    } = this.state;
    if (!preview && pending) {
      return (
        <div className={styles.centered}>
          <div
            className="cp-text-not-important"
            style={{
              display: 'inline-flex',
              alignItems: 'center'
            }}
          >
            <Icon type="loading" style={{marginRight: 5}} />
            <span>Loading preview...</span>
          </div>
        </div>
      );
    }
    if (!preview) {
      const errorText = error ? `Error loading preview: ${error}` : 'Error loading preview';
      return (
        <div>
          <Alert message={errorText} type="error" showIcon />
        </div>
      );
    }
    const {
      renderer: Renderer,
      data,
      storage,
      path
    } = preview;
    if (!Renderer) {
      return (
        <div className={styles.centered}>
          <Alert message="Unsupported file type" type="error" showIcon />
        </div>
      );
    }
    if (!storage) {
      return (
        <div className={styles.centered}>
          <Alert message="Storage not found" type="error" showIcon />
        </div>
      );
    }
    if (!path) {
      return (
        <div className={styles.centered}>
          <Alert message="File not found" type="error" showIcon />
        </div>
      );
    }
    return (
      <Renderer
        className={styles.filePreviewRenderer}
        filePath={path}
        fileData={data}
        storage={storage}
      />
    );
  };

  render () {
    const {
      className,
      style,
      header,
      footer
    } = this.props;
    return (
      <div
        className={classNames(styles.filePreview, className)}
        style={style}
      >
        {
          header && (
            <div className={styles.filePreviewHeader}>
              {header}
            </div>
          )
        }
        <div className={styles.filePreviewContent}>
          {this.renderContent()}
        </div>
        {
          footer && (
            <div className={styles.filePreviewFooter}>
              {footer}
            </div>
          )
        }
      </div>
    );
  }
}

FilePreview.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filePath: PropTypes.string,
  header: PropTypes.node,
  footer: PropTypes.node
};

export {FilePreview};
