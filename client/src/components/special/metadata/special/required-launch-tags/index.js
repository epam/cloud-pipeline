/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Input} from 'antd';
import {inject} from 'mobx-react';
import {computed} from 'mobx';

@inject('preferences')
class RequiredLaunchTags extends React.Component {
  static metadataKey = 'ui.runs.tags.defaults';

  state = {
    tagDefaults: undefined
  }

  componentDidMount () {
    this.setTagDefaultsFromProps();
  }

  componentDidUpdate (prevProps) {
    if (this.props.metadata?.value !== prevProps.metadata?.value) {
      this.setTagDefaultsFromProps();
    }
  }

  @computed
  get requiredRunTags () {
    return (this.props.preferences?.uiRunsTags || []).filter((tag) => !!tag.required);
  }

  setTagDefaultsFromProps = () => {
    const {metadata} = this.props;
    let tagDefaults;
    try {
      tagDefaults = JSON.parse(metadata.value || '{}');
    } catch (e) {
      return console.error('Error parsing Metadata RequiredLaunchTags: ', e);
    }
    const normalizedTagDefaults = {};
    Object.keys(tagDefaults).forEach(key => {
      normalizedTagDefaults[key.toLowerCase()] = tagDefaults[key];
    });
    this.setState({tagDefaults: normalizedTagDefaults});
  };

  apply = () => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(JSON.stringify(this.state.tagDefaults));
    }
  };

  onChange = (key) => (event) => {
    const {tagDefaults = {}} = this.state;
    this.setState({
      tagDefaults: {
        ...tagDefaults,
        [key.toLowerCase()]: event.target.value
      }
    });
  };

  render () {
    const {tagDefaults = {}} = this.state;
    return (
      <div style={{display: 'flex', flexDirection: 'column', gap: '4px'}}>
        <b>Required launch tags default values:</b>
        {this.requiredRunTags.map((tag) => (
          <div key={tag.tag} style={{display: 'flex', gap: '4px', alignItems: 'center'}}>
            <span>{tag.tag}:</span>
            <Input
              onBlur={this.apply}
              onPressEnter={this.apply}
              onChange={this.onChange(tag.tag)}
              value={tagDefaults[tag.tag.toLowerCase()]}
            />
          </div>
        ))}
      </div>
    );
  }
}

RequiredLaunchTags.propTypes = {
  metadata: PropTypes.object,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool,
  style: PropTypes.object
};

export default RequiredLaunchTags;
