/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observable} from 'mobx';
import {renderWithStores, screen, click, fn} from '@test';
import DockerImagesControl from './DockerImagesControl';

const stores = {
  dockerRegistries: observable({
    loaded: true,
    value: {registries: []},
    fetchIfNeededOrWait: () => Promise.resolve()
  })
};

const image1 = {id: 1, image: 'registry.com/group/image-1:latest', removed: false};
const image2 = {id: 2, image: 'registry.com/group/image-2:latest', removed: false};
const removedImage = {id: 3, image: 'registry.com/group/image-3:latest', removed: true};

function renderControl (props = {}) {
  return renderWithStores(
    <DockerImagesControl
      dockerImages={[image1, image2, removedImage]}
      disabled={false}
      readOnly={false}
      onChange={() => fn()}
      onRemove={() => fn()}
      onAdd={fn()}
      {...props}
    />,
    stores
  );
}

test('only non-removed entries render', () => {
  renderControl();
  // Each AddDockerRegistryControl renders its own delete button; two entries,
  // two delete buttons, plus "Add docker image".
  expect(screen.getAllByRole('button')).toHaveLength(3);
});

test('Add docker image calls onAdd and is disabled by disabled', async () => {
  const onAdd = fn();
  renderControl({onAdd, dockerImages: [], disabled: true});
  const addButton = screen.getByRole('button', {name: 'Add docker image'});
  expect(addButton).toBeDisabled();

  renderControl({onAdd, dockerImages: []});
  const enabledAddButton = screen.getAllByRole('button', {name: 'Add docker image'})[1];
  await click(enabledAddButton);
  expect(onAdd).toHaveBeenCalledTimes(1);
});

test('the Add button is absent when readOnly', () => {
  renderControl({readOnly: true, dockerImages: [image1]});
  expect(screen.queryByText('Add docker image')).not.toBeInTheDocument();
});

test('readOnly with zero images shows "Not specified"', () => {
  renderControl({readOnly: true, dockerImages: []});
  expect(screen.getByText('Not specified')).toBeInTheDocument();
});

test('readOnly with images shows neither "Not specified" nor the Add button', () => {
  renderControl({readOnly: true, dockerImages: [image1]});
  expect(screen.queryByText('Not specified')).not.toBeInTheDocument();
  expect(screen.queryByText('Add docker image')).not.toBeInTheDocument();
});

test('the error row shows only when both error and touched are truthy', () => {
  renderControl({dockerImages: [image1], error: 'Duplicates are not allowed', touched: false});
  expect(screen.queryByText('Duplicates are not allowed')).not.toBeInTheDocument();
});

test('the error row shows when both error and touched are set', () => {
  renderControl({dockerImages: [image1], error: 'Duplicates are not allowed', touched: true});
  expect(screen.getByText('Duplicates are not allowed')).toBeInTheDocument();
});

test('removing an entry calls onRemove with its id, then the curried inner fn', async () => {
  const inner = fn();
  const onRemove = fn(() => inner);
  renderControl({dockerImages: [image1], onRemove});

  // AddDockerRegistryControl's delete button carries no accessible name, so it
  // is picked by position rather than by role name.
  const deleteButton = screen.getAllByRole('button')[0];
  await click(deleteButton);

  expect(onRemove).toHaveBeenCalledWith(1);
  expect(inner).toHaveBeenCalledTimes(1);
});
