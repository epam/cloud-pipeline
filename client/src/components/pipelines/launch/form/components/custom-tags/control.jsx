import React from 'react';
import PropTypes from 'prop-types';
import CustomTagsButton from './button';
import CustomTagsEditor from './dialog';

class CustomTagsControl extends React.PureComponent {
  state = {
    visible: false
  };

  onOpen = () => this.setState({visible: true});
  onClose = () => this.setState({visible: false});
  onSave = (newTags) => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(newTags);
    }
    this.onClose();
  }

  render () {
    const {
      className,
      style,
      disabled,
      tags = {}
    } = this.props;

    const {visible} = this.state;

    return (
      <div
        className={className}
        style={style}
      >
        <CustomTagsButton
          tags={tags}
          disabled={disabled}
          onClick={this.onOpen}
        />
        <CustomTagsEditor
          visible={visible}
          onCancel={this.onClose}
          onSave={this.onSave}
          tags={tags}
        />
      </div>
    );
  }
}

CustomTagsControl.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  tags: PropTypes.object,
  onChange: PropTypes.func
};

export default CustomTagsControl;
