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

// Builders for the payloads the API returns, so a test states only the fields it
// cares about and the rest stay plausible. Plain data — no framework, no
// bundler, nothing that either migration touches.
//
// Add an entity factory here as a test needs it, built on `factory` below, and
// name it after the payload rather than the endpoint. Keep the defaults minimal:
// a factory that grows every optional field stops being readable.

/**
 * An incrementing id source, so two entities built in one test differ without
 * the test having to say so.
 *
 *   const nextId = sequence();
 *   nextId(); // 1
 */
export function sequence (start = 1) {
  let next = start;
  return () => {
    const value = next;
    next += 1;
    return value;
  };
}

/**
 * Builds a factory from a set of defaults. `defaults` may be a function, which
 * is called per build — use that for anything that has to be unique.
 *
 *   const folder = factory(() => ({id: nextId(), name: 'folder', parentId: null}));
 *   folder({name: 'projects'});
 */
export function factory (defaults) {
  return (overrides) => Object.assign(
    {},
    typeof defaults === 'function' ? defaults() : defaults,
    overrides
  );
}
