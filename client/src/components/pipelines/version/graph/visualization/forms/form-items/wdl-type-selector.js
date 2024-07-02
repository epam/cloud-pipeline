/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  AutoComplete
} from 'antd';
import {CompoundTypes, PrimitiveTypes} from '../../../../../../../utils/pipeline-builder';

function getSuggestions (typeString, structs = []) {
  const e = /([^[\],?+]+)$/.exec(typeString);
  let current = typeString || '';
  let filter = '';
  if (e && e[1]) {
    current = typeString.slice(0, e.index);
    filter = typeString.slice(e.index);
  }
  const suggest = (value) => ({
    value: `${current}${value}`,
    name: value
  });
  const filterSuggestion = (suggestion) => !filter.length || suggestion.startsWith(filter);
  if (
    /Map\[$/.test(current)
  ) {
    return [
      PrimitiveTypes.file,
      PrimitiveTypes.file,
      PrimitiveTypes.string,
      PrimitiveTypes.int,
      PrimitiveTypes.boolean,
      PrimitiveTypes.float
    ].filter(filterSuggestion).map(suggest);
  }
  if (
    /(Array|Pair)\[$/.test(current) ||
    /,$/i.test(current) ||
    current.length === 0
  ) {
    return [
      PrimitiveTypes.file,
      PrimitiveTypes.string,
      PrimitiveTypes.int,
      PrimitiveTypes.boolean,
      PrimitiveTypes.float,
      CompoundTypes.array,
      CompoundTypes.object,
      CompoundTypes.pair,
      CompoundTypes.map,
      ...[...new Set((structs || []).map((s) => s.alias))].filter(Boolean)
    ].filter(filterSuggestion).map(suggest);
  }
  return [];
}

function WdlTypeSelector (
  {
    className,
    style,
    disabled,
    value,
    onChange,
    structs
  }
) {
  const options = getSuggestions(value, structs)
    .map((o) => (
      <AutoComplete.Option key={o.value} vlaue={o.value}>
        {o.name}
      </AutoComplete.Option>
    ));
  return (
    <AutoComplete
      dataSource={options}
      value={value}
      onSearch={onChange}
      onChange={onChange}
      optionLabelProp="value"
      className={className}
      style={style}
      disabled={disabled}
      getPopupContainer={triggerNode => triggerNode.parentNode}
      readOnly={disabled}
      dropdownMatchSelectWidth={false}
    />
  );
}

WdlTypeSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  value: PropTypes.string,
  onChange: PropTypes.func
};

export default WdlTypeSelector;
