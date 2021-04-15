/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import mapObjectConfiguration from './map-object-configuration';
import {Alert} from 'antd';
import {inject, observer} from 'mobx-react';

const HIDDEN_OBJECTS_INJECTION = 'hiddenObjects';

export {HIDDEN_OBJECTS_INJECTION};

function HOC (...objects) {
  /*
    objects: iterable of {type: string, identifier: string|Func}
   */
  return function (WrappedComponent) {
    class Component extends React.Component {
      render () {
        const {props} = this;
        const {
          hiddenObjects
        } = props;
        const objectsToCheck = objects.map(mapObjectConfiguration(props));
        if (hiddenObjects && !hiddenObjects.loaded) {
          return null;
        }
        const hidden = objectsToCheck
          .find(o => hiddenObjects.isHidden(o.type, o.identifier));
        if (hidden) {
          return (
            <Alert type="warning" message="Access denied" />
          );
        }
        return (<WrappedComponent {...props} />);
      }
    }
    return inject(HIDDEN_OBJECTS_INJECTION)(observer(Component));
  };
}

export function checkObjectsHOC (type) {
  return function checkObjects (...objects) {
    return HOC(...objects.map(o => ({type, identifier: o})));
  };
}

export function checkObjectsWithParentHOC (type, parentOptional = false) {
  return function checkObjects (parent, ...objects) {
    const objectsToCheck = objects.map(o => ({type, identifier: o, parent}));
    if (parent && parentOptional) {
      objectsToCheck.push(
        ...objects.map(o => ({type, identifier: o}))
      );
    }
    return HOC(...objectsToCheck);
  };
}
