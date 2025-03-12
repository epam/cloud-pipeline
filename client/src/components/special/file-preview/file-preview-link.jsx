import React from 'react';
import PropTypes from 'prop-types';
import {FilePreviewModal} from './file-preview-modal';
import highlightText from '../highlightText';

class FilePreviewLink extends React.PureComponent {
  state = {
    visible: false
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.filePath !== this.props.filePath) {
      this.onClose();
    }
    if (prevState.visible !== this.state.visible) {
      const {
        onPreviewVisibilityChanged
      } = this.props;
      if (onPreviewVisibilityChanged) {
        onPreviewVisibilityChanged(this.state.visible);
      }
    }
  }

  onLinkClick = (event) => {
    const {
      preventDefault = true
    } = this.props;
    if (event && preventDefault) {
      event.stopPropagation();
      event.preventDefault();
    }
    this.onOpen();
  };

  onOpen = () => {
    this.setState({visible: true});
  }

  onClose = () => {
    this.setState({visible: false});
  }

  render () {
    const {
      className,
      style,
      filePath,
      title,
      header,
      footer,
      search
    } = this.props;
    const {
      visible
    } = this.state;
    return (
      <a className={className} style={style} onClick={this.onLinkClick}>
        {highlightText(filePath, search)}
        <FilePreviewModal
          filePath={filePath}
          visible={visible}
          onCancel={this.onClose}
          maskClosable
          title={title}
          header={header}
          footer={footer}
        />
      </a>
    );
  }
}

FilePreviewLink.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filePath: PropTypes.string,
  title: PropTypes.node,
  header: PropTypes.node,
  footer: PropTypes.node,
  preventDefault: PropTypes.bool,
  onPreviewVisibilityChanged: PropTypes.func,
  search: PropTypes.string
};

export {FilePreviewLink};
