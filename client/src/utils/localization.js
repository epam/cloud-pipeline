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
import {observable, computed} from 'mobx';
import {inject} from 'mobx-react';
import Controls from '../models/user/Controls';

class Localization {
  @observable _dictionaryRequest;
  constructor () {
    this._dictionaryRequest = new Controls();
    this._dictionaryRequest.fetch();
  }

  @computed
  get dictionary () {
    if (!this._dictionaryRequest.loaded) {
      return [];
    }
    return (this._dictionaryRequest.value || []).map(c => c);
  }

  getDictionaryElement = (key, extraDictionary = []) => {
    return [...this.dictionary, ...extraDictionary]
      .filter(d => (d.key || '').toLowerCase() === (key || '').toLowerCase())[0];
  };

  localizedString = (key, extraDictionary = []) => {
    const dictionaryElement = this.getDictionaryElement(key, extraDictionary);
    if (key && dictionaryElement) {
      const uppercased = key.toUpperCase() === key;
      const lowercased = key.toLowerCase() === key;
      const capitalized = key[0].toUpperCase() === key[0] &&
        key.substring(1).toLowerCase() === key.substring(1);
      if (uppercased) {
        return (dictionaryElement.value || '').toUpperCase();
      } else if (lowercased) {
        return (dictionaryElement.value || '').toLowerCase();
      } else if (capitalized) {
        const value = (dictionaryElement.value || '').toLowerCase();
        return `${value[0].toUpperCase()}${value.substring(1)}`;
      } else {
        return (dictionaryElement.value || '');
      }
    }
    return key;
  };
}

const localizedComponent = (...opts) => inject('localization')(...opts);

class LocalizedReactComponent extends React.Component {
  localizedString = (key, extra) => this.props.localization
    ? this.props.localization.localizedString(key, extra)
    : key;
}

export default {
  localizedComponent,
  Localization,
  LocalizedReactComponent
};
