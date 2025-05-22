import React from 'react';
import {Modal, Button} from 'antd';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import UIRunUserTag from './ui-run-user-tag';
import {
  getRequiredUserTags,
  getUserTagsValidationResult
} from '../../../../../runs/run-tags/utilities';

@inject('preferences')
@observer
class CustomTagsEditor extends React.Component {
  state = {
    tags: {},
    validation: [],
    required: []
  };

  componentDidMount () {
    this.updateFromProps();
    this.updateRequiredTags();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.tags !== prevProps.tags ||
      this.props.visible !== prevProps.visible
    ) {
      this.updateFromProps();
    }
    if (this.props.payload !== prevProps.payload) {
      this.updateRequiredTags();
    }
  }

  componentWillUnmount () {
    this._token = {};
    this._validationToken = {};
  }

  updateFromProps = () => {
    const {
      tags: propsTags = {}
    } = this.props;
    this.setState({
      tags: {...propsTags}
    }, this.updateValidation);
  };

  updateRequiredTags = () => {
    const {payload} = this.props;
    const current = this._token = {};
    const commit = (fn) => {
      if (current === this._token) {
        if (typeof fn === 'function') {
          fn();
        } else {
          this.setState(fn);
        }
      }
    };
    (async () => {
      const required = await getRequiredUserTags(payload);
      commit(() => this.setState({required}, () => this.updateValidation()));
    })();
  };

  updateValidation = () => {
    const {tags, required: requiredTags = []} = this.state;
    const current = this._validationToken = {};
    const commit = (fn) => {
      if (current === this._validationToken) {
        if (typeof fn === 'function') {
          fn();
        } else {
          this.setState(fn);
        }
      }
    };
    (async () => {
      const validation = await getUserTagsValidationResult(tags, {requiredTags});
      commit(() => this.setState({validation}));
    })();
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

  onChangeTagValue = (tag, value) => {
    const {tags} = this.state;
    this.setState({tags: {...tags, [tag.tag]: value}}, this.updateValidation);
  };

  render () {
    const {visible} = this.props;
    const {tags, validation = []} = this.state;

    return (
      <Modal
        visible={visible}
        title="Specify job tags"
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
              onChange={(value) => this.onChangeTagValue(tag, value)}
              validation={validation.find((o) => o.tag === tag.tag)}
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
  tags: PropTypes.object,
  payload: PropTypes.object
};

export default CustomTagsEditor;
