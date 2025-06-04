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
      tags = {},
      validation = [],
      payload,
      buttonText
    } = this.props;

    const {visible} = this.state;

    return (
      <div
        className={className}
        style={style}
      >
        <CustomTagsButton
          tags={tags}
          validation={validation}
          payload={payload}
          disabled={disabled}
          onClick={this.onOpen}
          buttonText={buttonText}
        />
        <CustomTagsEditor
          visible={visible}
          onCancel={this.onClose}
          onSave={this.onSave}
          tags={tags}
          payload={payload}
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
  validation: PropTypes.oneOfType(PropTypes.object, PropTypes.array),
  payload: PropTypes.object,
  onChange: PropTypes.func,
  buttonText: PropTypes.node
};

export default CustomTagsControl;
