import React from 'react';
import PropTypes from 'prop-types';
import Input from 'antd/es/input';
import BucketBrowser from './bucket-browser';
import 'antd/es/input/style/css';
import {info} from '../api/storage';

class BucketInput extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    onChange: PropTypes.func,
    storageId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    value: PropTypes.string
  };

  state = {
    browserVisible: false,
    value: null
  };

  componentDidMount() {
    this.setState({
      value: this.props.value
    });
    const {storageId} = this.props;
    info(storageId).then(result => {
      if (!result.error && result.pathMask) {
        this.setState({
          pathMask: result.pathMask,
          info: result
        });
      }
    });
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (prevProps.value !== this.props.value) {
      this.setState({
        value: this.props.value
      });
    }
  }

  onChange = (e) => {
    const {onChange} = this.props;
    onChange(e ? e.target.value : null);
  };

  onBrowseChange = (e) => {
    const {onChange} = this.props;
    onChange(e);
    this.onCloseBrowser();
  };

  onBrowseClicked = () => {
    const {disabled} = this.props;
    if (disabled) {
      return;
    }
    this.setState({
      browserVisible: true
    });
  };

  onCloseBrowser = () => {
    this.setState({
      browserVisible: false
    });
  };

  render () {
    const {
      disabled,
      storageId
    } = this.props;
    const {
      browserVisible,
      pathMask,
      value
    } = this.state;
    return (
      <>
        <Input
          value={value}
          disabled={disabled}
          onChange={this.onChange}
          addonBefore={pathMask ? (<div>{pathMask}/</div>) : undefined}
          addonAfter={(
            <div style={{cursor: 'pointer'}} onClick={this.onBrowseClicked}>
              Browse
            </div>
          )}
        />
        <BucketBrowser
          visible={browserVisible}
          onClose={this.onCloseBrowser}
          value={value}
          onChange={this.onBrowseChange}
          storageId={storageId}
        />
      </>
    );
  }
}

export default BucketInput;
