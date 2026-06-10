import React from 'react';
import PropTypes from 'prop-types';
import CustomTagsButton from './button';
import CustomTagsEditor from './dialog';

class CustomTagsControl extends React.PureComponent {
  state = {
    visible: false,
  };

  onOpen = () => this.setState({visible: true});
  onClose = () => this.setState({visible: false});
  onSave = (newTags, tagsTouched) => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(newTags, tagsTouched);
    }
    this.onClose();
  };

  render() {
    const {
      className,
      style,
      disabled,
      tags = {},
      validation = [],
      visibleTags = [],
      payload,
      buttonText,
    } = this.props;

    const {visible} = this.state;

    return (
      <div className={className} style={style}>
        <CustomTagsButton
          tags={tags}
          validation={validation}
          visibleTags={visibleTags}
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
  validation: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  visibleTags: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  payload: PropTypes.object,
  onChange: PropTypes.func,
  buttonText: PropTypes.node,
};

export default CustomTagsControl;
