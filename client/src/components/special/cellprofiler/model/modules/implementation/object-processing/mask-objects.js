/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
/* eslint-disable max-len */

export default {
  name: 'MaskObjects',
  group: 'Object Processing',
  output: 'output|object',
  parameters: [
    'Select objects to be masked|object|ALIAS input',
    'Name the masked objects|string|MaskedObjects|ALIAS output',
    'Mask using a region defined by other objects or by binary image?|[Objects,Image]|Objects|ALIAS method',
    'Select the masking object|object|ALIAS maskingObject|IF method==Objects',
    'Select the masking image|file|ALIAS maskingImage|IF method==Image',
    'Invert the mask?|flag|false|ALIAS invert',
    'Handling of objects that are partially masked|[Keep,Remove,Keep overlapping region,Remove depending on overlap]|Keep overlapping region|ALIAS overlap',
    'Fraction of object that must overlap|float|0.5|IF overlap=="Remove depending on overlap"',
    'Numbering of resulting objects|[Renumber,Retain]|Renumber'
  ]
};
