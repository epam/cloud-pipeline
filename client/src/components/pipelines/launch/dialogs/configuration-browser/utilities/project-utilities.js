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

import MetadataEntityFields from '../../../../../../models/folderMetadata/MetadataEntityFields';

export async function getProjectEntityTypes (projectId) {
  const request = new MetadataEntityFields(projectId);
  await request.fetch();
  if (request.error) {
    throw new Error(request.error);
  }
  return (request.value || []).map(({fields, metadataClass}) => ({
    ...metadataClass,
    fields
  }));
}

export async function getProjectEntityTypeByName (projectId, name) {
  const classes = await getProjectEntityTypes(projectId);
  return classes.find(o => name &&
    o.name &&
    o.name.toLowerCase() === name.toLowerCase()
  );
}
