import React from 'react';
import {Modal, Button} from 'antd';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import UIRunUserTag from './ui-run-user-tag';

@inject('preferences')
@observer
class CustomTagsEditor extends React.Component {
  state = {
    tags: {}
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.tags !== prevProps.tags ||
      this.props.visible !== prevProps.visible
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      tags: propsTags = {}
    } = this.props;
    this.setState({
      tags: {...propsTags}
    });
  };

  @computed
  get uiRunsUserTags () {
    const {preferences} = this.props;
    return preferences.uiRunsUserTags;
  }

  handleCancel = () => {
    const {onCancel} = this.props;

    if (onCancel) {
      onCancel();
    }
  };

  handleSave = () => {
    const {tags} = this.state;
    const filtered = Object.entries(tags ?? {})
      .map(([key, value]) => ({key, value}))
      .filter((o) => o.value && o.value.trim().length > 0)
      .reduce((acc, current) => ({
        ...acc,
        [current.key]: current.value
      }), {});
    this.props.onSave(filtered);
  }

  render () {
    const {visible} = this.props;
    const {tags} = this.state;

    return (
      <Modal
        visible={visible}
        title="Specify job custom tags"
        onCancel={this.handleCancel}
        footer={[
          <Button key="back" onClick={this.handleCancel}>Cancel</Button>,
          <Button key="submit" type="primary" onClick={this.handleSave}>
            Save
          </Button>
        ]}
        bodyStyle={{maxHeight: 'max(50vh, 300px)', overflow: 'auto'}}
      >
        {
          this.uiRunsUserTags.map((tag) => (
            <UIRunUserTag
              key={tag.tag}
              style={{width: '100%', margin: '10px 0'}}
              tagConfiguration={tag}
              tagValue={tags[tag.tag] ?? ''}
              onChange={(value) => this.setState({tags: {...tags, [tag.tag]: value}})}
            />
          ))
        }
      </Modal>
    );
  }
}

CustomTagsEditor.propTypes = {
  visible: PropTypes.bool.isRequired,
  onCancel: PropTypes.func.isRequired,
  onSave: PropTypes.func.isRequired,
  tags: PropTypes.object
};

export default CustomTagsEditor;
