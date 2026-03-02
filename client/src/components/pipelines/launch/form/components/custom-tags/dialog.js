import React from 'react';
import {Modal, Button} from 'antd';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed, makeObservable} from 'mobx';
import UIRunUserTag from './ui-run-user-tag';
import {
  getRequiredUserTags,
  getUserTagsValidationResult,
  getVisibleUserTags
} from '../../../../../runs/run-tags/utilities';

@inject('preferences')
@observer
class CustomTagsEditor extends React.Component {
  state = {
    tags: {},
    validation: [],
    required: [],
    visible: [],
    tagsTouched: []
  };

  constructor (props) {
    super(props);
    makeObservable(this, {
      uiRunsUserTags: computed
    });
  }

  componentDidMount () {
    this.updateFromProps();
    this.updateRequiredAndVisibleTags();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.tags !== prevProps.tags ||
      this.props.visible !== prevProps.visible
    ) {
      this.updateFromProps();
    }
    if (this.props.payload !== prevProps.payload) {
      this.updateRequiredAndVisibleTags();
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

  updateRequiredAndVisibleTags = () => {
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
      const [
        required,
        visible
      ] = await Promise.all([
        getRequiredUserTags(payload),
        getVisibleUserTags(payload)
      ]);
      commit(() => this.setState({required, visible}, () => this.updateValidation()));
    })();
  };

  updateValidation = () => {
    const {
      tags,
      required: requiredTags = [],
      visible: visibleTags = []
    } = this.state;
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
      const validation = await getUserTagsValidationResult(
        tags,
        {requiredTags, visibleTags}
      );
      commit(() => this.setState({validation}));
    })();
  };

  get uiRunsUserTags () {
    const {preferences} = this.props;
    const {visible: visibleTags = []} = this.state;
    return (preferences.uiRunsUserTags || []).filter((tag) => visibleTags.includes(tag.tag));
  }

  handleCancel = () => {
    const {onCancel} = this.props;

    if (onCancel) {
      onCancel();
    }
  };

  handleSave = () => {
    const {tags, tagsTouched} = this.state;
    const filtered = Object.entries(tags ?? {})
      .map(([key, value]) => ({key, value}))
      .filter((o) => o.value && o.value.trim().length > 0)
      .reduce((acc, current) => ({
        ...acc,
        [current.key]: current.value
      }), {});
    this.props.onSave(
      filtered,
      tagsTouched
    );
  }

  onChangeTagValue = (tag, value) => {
    const {tags, tagsTouched} = this.state;
    const touchedKeys = [...new Set([...tagsTouched, tag.tag])];
    this.setState({
      tags: {...tags, [tag.tag]: value},
      tagsTouched: touchedKeys
    }, this.updateValidation);
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
