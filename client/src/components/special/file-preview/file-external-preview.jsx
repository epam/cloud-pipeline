import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import {Button} from 'antd';
import {getStorageLinkInfo} from '../data-storage-link/utilities';
import {getStaticResourceUrl} from '../../../models/static-resources';

@inject('preferences', 'dataStorageAvailable')
@observer
class FileExternalPreview extends React.Component {
  state = {
    info: undefined
  };

  componentDidMount () {
    this.updateFileInfo();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.filePath !== this.props.filePath) {
      this.updateFileInfo();
    }
  }

  componentWillUnmount () {
    this.token = {};
  }

  updateFileInfo = () => {
    const token = this.token = {};
    const commit = (fn) => {
      if (token === this.token) {
        fn();
      }
    };
    const {
      dataStorageAvailable,
      filePath
    } = this.props;
    (async () => {
      try {
        await dataStorageAvailable.fetchIfNeededOrWait();
        const storages = dataStorageAvailable.value || [];
        const info = getStorageLinkInfo({
          storages,
          path: filePath,
          isFolder: false
        });
        commit(() => this.setState({
          info
        }));
      } catch {
        // noop
      }
    })();
  };

  onClick = (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    const {info} = this.state;
    const {
      storage,
      relativePath
    } = info ?? {};
    if (storage && relativePath) {
      const url = getStaticResourceUrl(storage.name, relativePath);
      window.open(url, '_blank');
    }
  };

  render () {
    const {
      className,
      style,
      disabled,
      mode = 'button',
      children = 'Open preview',
      preferences,
      size,
      primary = false,
      checkPreviewAvailability = true
    } = this.props;
    const {
      info
    } = this.state;
    const {
      relativePath,
      storage
    } = info || {};
    if (!relativePath || !storage) {
      return null;
    }
    const externalPreviewAvailable = preferences.dataStorageItemPreviewMasks
      .some(mask => mask.test(relativePath));
    if (checkPreviewAvailability && !externalPreviewAvailable) {
      return null;
    }
    if (/^link$/i.test(mode)) {
      if (disabled) {
        return (
          <span
            className={classNames(className, 'cp-text-not-important')}
            style={style}
          >
            {children}
          </span>
        );
      }
      return (
        <a onClick={this.onClick}>
          {children}
        </a>
      );
    }
    return (
      <Button
        className={className}
        style={style}
        size={size}
        onClick={this.onClick}
        type={primary ? 'primary' : undefined}
        disabled={disabled}
      >
        {children}
      </Button>
    );
  }
}

FileExternalPreview.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node,
  disabled: PropTypes.bool,
  filePath: PropTypes.string,
  mode: PropTypes.oneOf(['link', 'button']),
  checkPreviewAvailability: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium', 'large']), // only for 'button' mode
  primary: PropTypes.bool // only for 'button' mode
};

export {FileExternalPreview};
