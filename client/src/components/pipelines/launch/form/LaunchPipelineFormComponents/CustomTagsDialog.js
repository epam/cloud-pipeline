import React from 'react';
import { Modal, Button, Input, Row, Col } from 'antd';
import PropTypes from 'prop-types';

class CustomTagsEditor extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      tags: props.allowedTags || {}
    };
  }

  updateTags = (updatedTag, newValue) => {
    this.setState(({tags}) => ({
      tags: {
        ...tags,
        [updatedTag]: newValue
      }
    }));
  };

  handleCancel = () => {
    const {onCancel} = this.props;

    if (onCancel) {
      onCancel();
    }
  };

  handleSave = () => {
    const filteredTags = Object.fromEntries(
      Object.entries(this.state.tags).filter(([_, value]) => value !== '')
    );

    this.props.onSave(filteredTags);
  }

  render () {
    const {visible} = this.props;
    const {tags} = this.state;

    return (
      <Modal
        visible={visible}
        title="Customize Tags"
        onCancel={this.handleCancel}
        footer={[
          <Button key="back" onClick={this.handleCancel}>Cancel</Button>,
          <Button key="submit" type="primary" onClick={this.handleSave}>
            Save
          </Button>
        ]}
      >
        {Object.keys(tags).map((tag, i) => (
          <Row
            key={tag}
            gutter={8}
            style={{marginTop: i === 0 ? 0 : '10px', display: 'flex', alignItems: 'center'}}>
            <Col height="100%" span={6}>
              <span>{tag}</span>
            </Col>
            <Col span={12}>
              <Input
                value={tags[tag]}
                placeholder="Enter value"
                onChange={(e) => this.updateTags(tag, e.target.value)}
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
  allowedTags: PropTypes.objectOf(PropTypes.string)
};

export default CustomTagsEditor;
