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

// The unit suite's network seam. test/setup/env.js makes `global.fetch` throw;
// `stubApi()` replaces it for the duration of one test with something that
// speaks the platform's Result<T> envelope.
//
// Nothing here is bundler- or framework-specific, so it survives both
// migrations untouched.

import {apiPrefix} from './config';

/** `{status: 'OK', payload}` — what the API returns when it worked. */
export function ok (payload) {
  return {status: 'OK', payload};
}

/** An application-level failure: HTTP 200 carrying a non-OK envelope. */
export function fail (message, status = 'ERROR') {
  return {status, message};
}

/** The envelope that drives Remote's logout path. */
export function unauthorized (message = 'Unauthorized') {
  return {status: 401, message};
}

/** A 200 carrying `body` as JSON. Exported for a handler that needs the exact
 * Response — everything else goes through the helpers on the stub. */
export function jsonResponse (body, init = {}) {
  return new Response(JSON.stringify(body), Object.assign({
    status: 200,
    statusText: 'OK',
    headers: {'Content-Type': 'application/json'}
  }, init));
}

// Turns whatever fetch was called with into the path a route is registered by.
function relativePath (url) {
  const value = String(url);
  return value.indexOf(apiPrefix) === 0 ? value.slice(apiPrefix.length) : value;
}

function matches (pattern, url, relative) {
  if (pattern instanceof RegExp) {
    return pattern.test(relative) || pattern.test(url);
  }
  return pattern === relative || pattern === url;
}

let active = null;

/**
 * Installs a fake `global.fetch` and returns a handle to configure it.
 *
 *   const api = stubApi({'/folder/1/load': {id: 1, name: 'root'}});
 *   api.fail('/folder/2/load', 'No such folder');
 *   ...
 *   expect(api.calls).toHaveLength(1);
 *
 * The map form registers OK envelopes, keyed by the path relative to the API
 * prefix. A request with no matching route rejects with a message naming it and
 * everything that is registered, so a typo is legible rather than a hang.
 *
 * Restored automatically after each test.
 */
export function stubApi (routes) {
  restoreApi();

  const handlers = [];
  const calls = [];
  const previousFetch = global.fetch;

  function register (pattern, reply) {
    handlers.push({pattern, reply});
    return stub;
  }

  const stub = {
    /** Every request the stub saw: `{url, path, method, body, options}`. */
    calls,

    /** Replies with an OK envelope carrying `payload`. */
    reply (pattern, payload) {
      return register(pattern, () => jsonResponse(ok(payload)));
    },

    /** Replies with a non-OK envelope, i.e. an application-level failure. */
    fail (pattern, message, status) {
      return register(pattern, () => jsonResponse(fail(message, status)));
    },

    /** Replies with an envelope verbatim — for 401 and other exact shapes. */
    envelope (pattern, body) {
      return register(pattern, () => jsonResponse(body));
    },

    /** Replies at the transport level: a non-2xx status with any body. */
    status (pattern, httpStatus, body = {}, statusText = '') {
      return register(pattern, () => jsonResponse(body, {
        status: httpStatus,
        statusText
      }));
    },

    /** Rejects, the way a fetch does when the network is unreachable. */
    networkError (pattern, message = 'Failed to fetch') {
      return register(pattern, () => Promise.reject(new Error(message)));
    },

    /**
     * Full control: `reply(request)` returns a Response, or anything else, which
     * is serialised as the JSON body of a 200. `request` is the recorded call.
     */
    handle (pattern, reply) {
      return register(pattern, reply);
    },

    restore () {
      if (active === stub) {
        active = null;
        global.fetch = previousFetch;
      }
    }
  };

  Object.keys(routes || {}).forEach(pattern => {
    stub.reply(pattern, routes[pattern]);
  });

  global.fetch = function (url, options = {}) {
    const path = relativePath(url);
    const call = {
      url: String(url),
      path,
      method: (options.method || 'GET').toUpperCase(),
      body: options.body,
      options
    };
    calls.push(call);
    const handler = handlers.find(entry => matches(entry.pattern, call.url, path));
    if (!handler) {
      const registered = handlers.length
        ? handlers.map(entry => String(entry.pattern)).join(', ')
        : '(none)';
      return Promise.reject(new Error(
        `stubApi: no route for ${call.method} ${path}. Registered: ${registered}`
      ));
    }
    return Promise.resolve(handler.reply(call)).then(result =>
      result instanceof Response ? result : jsonResponse(result)
    );
  };

  active = stub;
  return stub;
}

/** Puts the network guard back. Called for you after each test. */
export function restoreApi () {
  if (active) {
    active.restore();
  }
}

if (typeof afterEach === 'function') {
  afterEach(restoreApi);
}
