/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {Button, Input} from 'antd';
import PropTypes from 'prop-types';

function DockerRegistriesGroupsSearch (
  {
    onGroupSearch,
    groupSearch,
    onCancel
  }
) {
  const onSearch = (e) => {
    if (onGroupSearch) {
      onGroupSearch(e.target.value);
    }
  };

  const onSearchClear = () => {
    if (onGroupSearch) {
      onGroupSearch();
    }
  };

  const onKeyDown = (e) => {
    if (onCancel && e.key && e.key.toLowerCase() === 'escape') {
      onCancel();
    }
  };

  const disabled = !groupSearch || !groupSearch.length;
  return (
    <div
      style={{
        padding: '13px 13px 4px',
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center'
      }}
    >
      <Input.Search
        onKeyDown={onKeyDown}
        id="groups-search-input"
        value={groupSearch}
        onChange={onSearch}
        style={{flex: 1}}
        size="small"
        placeholder="Search tool groups"
      />
      <Button
        style={{marginLeft: 5}}
        onClick={onSearchClear}
        disabled={disabled}
        size="small"
      >
        Clear
      </Button>
    </div>
  );
}

DockerRegistriesGroupsSearch.propTypes = {
  groupSearch: PropTypes.string,
  onGroupSearch: PropTypes.func,
  onCancel: PropTypes.func
};

export default DockerRegistriesGroupsSearch;
