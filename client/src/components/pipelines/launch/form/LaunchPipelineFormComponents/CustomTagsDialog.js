import React from 'react';
import {Modal, Button, Input, Row, Col} from 'antd';
import PropTypes from 'prop-types';

class CustomTagsEditor extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      tagInput: '',
      displayInput: '',
      tags: props.initialTags || []
    };
  }

  handleInputChange = (e) => {
    this.setState({tagInput: e.target.value});
  };

  handleDisplayChange = (e) => {
    this.setState({displayInput: e.target.value});
  };

  addTag = () => {
    const {tagInput, displayInput, tags} = this.state;
    const trimmedTag = tagInput.trim();
    if (
      trimmedTag &&
      !tags.find(t => t.tag.toLowerCase() === trimmedTag.toLowerCase())
    ) {
      this.setState({
        tags: [
          ...tags,
          {
            tag: trimmedTag,
            display: displayInput.trim(),
            user_tag: true
          }
        ],
        tagInput: '',
        displayInput: ''
      });
    }
  };

  updateDisplay = (updatedTag, newDisplay) => {
    this.setState(({tags}) => ({
      tags: tags.map(tag =>
        tag.tag === updatedTag ? {...tag, display: newDisplay} : tag
      )
    }));
  };

  removeTag = (removedTagKey) => {
    this.setState(({tags}) => ({
      tags: tags.filter(tag => tag.tag !== removedTagKey)
    }));
  };

  handleCancel = () => {
    const {onCancel, initialTags = []} = this.props;
    this.setState({tagInput: '', displayInput: '', tags: initialTags});

    if (onCancel) {
      onCancel();
    }
  };

  render () {
    const {visible, onSave} = this.props;
    const {tags} = this.state;

    return (
      <Modal
        visible={visible}
        title="Customize Tags"
        onCancel={this.handleCancel}
        onOk={() => onSave(tags)}
        footer={[
          <Button key="back" onClick={this.handleCancel}>Cancel</Button>,
          <Button key="submit" type="primary" onClick={() => onSave(tags)}>
            Save
          </Button>
        ]}
      >
        {tags.map((tag, i) => (
          <Row align="middle" key={tag.tag} gutter={8} style={{marginTop: i === 0 ? 0 : '10px'}}>
            <Col span={6}>
              <span>
                {tag.tag}
              </span>
            </Col>
            <Col span={12}>
              <Input
                value={tag.display}
                placeholder="Edit display value"
                onChange={(e) => this.updateDisplay(tag.tag, e.target.value)}
              />
            </Col>
          </Row>
        ))}
      </Modal>
    );
  }
}

CustomTagsEditor.propTypes = {
  visible: PropTypes.bool.isRequired,
  onCancel: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
  initialTags: PropTypes.arrayOf(
    PropTypes.shape({
      tag: PropTypes.string.isRequired,
      user_tag: PropTypes.bool,
      color: PropTypes.string,
      display: PropTypes.string
    })
  )
};

export default CustomTagsEditor;
