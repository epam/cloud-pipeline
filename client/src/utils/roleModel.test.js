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

import roleModel from './roleModel';

// The mask predicates. client/AGENTS.md tells contributors not to hand-roll
// bit arithmetic on `mask` and to go through these instead, which is only a safe
// instruction while they are covered.
const {
  readAllowed,
  writeAllowed,
  executeAllowed,
  readDenied,
  writeDenied,
  executeDenied,
  isOwner,
  readPermissionEnabled,
  writePermissionEnabled,
  executePermissionEnabled
} = roleModel;

// The plain 3-bit mask the API puts on an entity.
const READ = 1;
const WRITE = 2;
const EXECUTE = 4;
const OWNER = 8;

// The extended 6-bit mask, an allow/deny pair per permission, used where "not
// allowed" and "explicitly denied" have to be told apart.
const EXT_READ_ALLOW = 1;
const EXT_READ_DENY = 2;
const EXT_WRITE_ALLOW = 4;
const EXT_WRITE_DENY = 8;
const EXT_EXECUTE_ALLOW = 16;
const EXT_EXECUTE_DENY = 32;

// The six that take an `extendedMask` flag. `isOwner` deliberately does not:
// the extended mask carries no ownership bit for it to read.
const extendedCapable = {
  readAllowed,
  writeAllowed,
  executeAllowed,
  readDenied,
  writeDenied,
  executeDenied
};

const nothing = [
  ['no item', undefined],
  ['a null item', null],
  ['an item without a mask', {}],
  ['an item with a null mask', {mask: null}]
];

describe('roleModel mask predicates, on an item with no mask', () => {
  Object.keys(extendedCapable).forEach(name => {
    nothing.forEach(([label, item]) => {
      it(`${name} says false for ${label}, on either mask format`, () => {
        expect(extendedCapable[name](item)).toBe(false);
        expect(extendedCapable[name](item, true)).toBe(false);
      });
    });
  });

  nothing.forEach(([label, item]) => {
    it(`isOwner says false for ${label}`, () => {
      expect(isOwner(item)).toBe(false);
    });
  });
});

describe('roleModel mask predicates, on a plain mask', () => {
  it('reads each permission off its own bit', () => {
    expect(readAllowed({mask: READ})).toBe(true);
    expect(writeAllowed({mask: WRITE})).toBe(true);
    expect(executeAllowed({mask: EXECUTE})).toBe(true);
  });

  it('does not confuse one permission for another', () => {
    expect(readAllowed({mask: WRITE | EXECUTE})).toBe(false);
    expect(writeAllowed({mask: READ | EXECUTE})).toBe(false);
    expect(executeAllowed({mask: READ | WRITE})).toBe(false);
  });

  it('grants everything on a full mask and nothing on an empty one', () => {
    const full = {mask: READ | WRITE | EXECUTE};
    expect([
      readAllowed(full),
      writeAllowed(full),
      executeAllowed(full)
    ]).toEqual([true, true, true]);
    const empty = {mask: 0};
    expect([
      readAllowed(empty),
      writeAllowed(empty),
      executeAllowed(empty)
    ]).toEqual([false, false, false]);
  });

  it('treats denied as the negation of allowed, since a plain mask has no deny bit', () => {
    expect(readDenied({mask: 0})).toBe(true);
    expect(readDenied({mask: READ})).toBe(false);
    expect(writeDenied({mask: READ})).toBe(true);
    expect(writeDenied({mask: WRITE})).toBe(false);
    expect(executeDenied({mask: READ | WRITE})).toBe(true);
    expect(executeDenied({mask: EXECUTE})).toBe(false);
  });

  it('reads ownership off its own bit, independently of the permissions', () => {
    expect(isOwner({mask: OWNER})).toBe(true);
    expect(isOwner({mask: READ | WRITE | EXECUTE | OWNER})).toBe(true);
    expect(isOwner({mask: READ | WRITE | EXECUTE})).toBe(false);
    expect(isOwner({mask: 0})).toBe(false);
  });

  it('ignores bits above the ones it knows', () => {
    expect(readAllowed({mask: READ | (1 << 6)})).toBe(true);
    expect(isOwner({mask: (1 << 6)})).toBe(false);
  });
});

describe('roleModel mask predicates, on an extended mask', () => {
  it('reads each allow bit', () => {
    expect(readAllowed({mask: EXT_READ_ALLOW}, true)).toBe(true);
    expect(writeAllowed({mask: EXT_WRITE_ALLOW}, true)).toBe(true);
    expect(executeAllowed({mask: EXT_EXECUTE_ALLOW}, true)).toBe(true);
  });

  it('does not read one permission out of another permission\'s bits', () => {
    expect(readAllowed({mask: EXT_WRITE_ALLOW | EXT_EXECUTE_ALLOW}, true)).toBe(false);
    expect(writeAllowed({mask: EXT_READ_ALLOW | EXT_EXECUTE_ALLOW}, true)).toBe(false);
    expect(executeAllowed({mask: EXT_READ_ALLOW | EXT_WRITE_ALLOW}, true)).toBe(false);
  });

  it('reads each deny bit', () => {
    expect(readDenied({mask: EXT_READ_DENY}, true)).toBe(true);
    expect(writeDenied({mask: EXT_WRITE_DENY}, true)).toBe(true);
    expect(executeDenied({mask: EXT_EXECUTE_DENY}, true)).toBe(true);
  });

  it('does not read one denial out of another permission\'s bits', () => {
    expect(readDenied({mask: EXT_WRITE_DENY | EXT_EXECUTE_DENY}, true)).toBe(false);
    expect(writeDenied({mask: EXT_READ_DENY | EXT_EXECUTE_DENY}, true)).toBe(false);
    expect(executeDenied({mask: EXT_READ_DENY | EXT_WRITE_DENY}, true)).toBe(false);
  });

  it('tells an inherited permission apart from a denied one', () => {
    // This is the whole reason the extended format exists: with neither bit set
    // the rule is inherited, so it is neither allowed nor denied — a distinction
    // a plain mask cannot make.
    const inherited = {mask: 0};
    expect(readAllowed(inherited, true)).toBe(false);
    expect(readDenied(inherited, true)).toBe(false);
    const denied = {mask: EXT_READ_DENY};
    expect(readAllowed(denied, true)).toBe(false);
    expect(readDenied(denied, true)).toBe(true);
  });

  it('reports allow and deny independently when both bits are set', () => {
    const contradictory = {mask: EXT_READ_ALLOW | EXT_READ_DENY};
    expect(readAllowed(contradictory, true)).toBe(true);
    expect(readDenied(contradictory, true)).toBe(true);
  });

  it('grants everything on a fully-allowing mask', () => {
    const full = {mask: EXT_READ_ALLOW | EXT_WRITE_ALLOW | EXT_EXECUTE_ALLOW};
    expect([
      readAllowed(full, true),
      writeAllowed(full, true),
      executeAllowed(full, true),
      readDenied(full, true),
      writeDenied(full, true),
      executeDenied(full, true)
    ]).toEqual([true, true, true, false, false, false]);
  });

  it('denies everything on a fully-denying mask', () => {
    const none = {mask: EXT_READ_DENY | EXT_WRITE_DENY | EXT_EXECUTE_DENY};
    expect([
      readAllowed(none, true),
      writeAllowed(none, true),
      executeAllowed(none, true),
      readDenied(none, true),
      writeDenied(none, true),
      executeDenied(none, true)
    ]).toEqual([false, false, false, true, true, true]);
  });
});

describe('roleModel permission-enabled checks', () => {
  // A different question from the predicates above: not "is this permission
  // allowed" but "does this mask say anything about it at all", which is how a
  // caller picks the permissions worth checking for conflicts.
  const enabled = mask => [
    readPermissionEnabled(mask),
    writePermissionEnabled(mask),
    executePermissionEnabled(mask)
  ];

  it('says nothing is mentioned by an empty mask', () => {
    expect(enabled(0)).toEqual([false, false, false]);
  });

  it('reads each permission from its own pair of bits', () => {
    expect(enabled(EXT_READ_ALLOW)).toEqual([true, false, false]);
    expect(enabled(EXT_WRITE_ALLOW)).toEqual([false, true, false]);
    expect(enabled(EXT_EXECUTE_ALLOW)).toEqual([false, false, true]);
  });

  it('counts a deny bit, not just an allow bit', () => {
    // The bit that a missing pair of parentheses used to drop: with `>` binding
    // tighter than `&`, the mask collapsed to 1 and only the allow bit was read,
    // so a deny-only pair looked like no permission at all.
    expect(enabled(EXT_READ_DENY)).toEqual([true, false, false]);
    expect(enabled(EXT_WRITE_DENY)).toEqual([false, true, false]);
    expect(enabled(EXT_EXECUTE_DENY)).toEqual([false, false, true]);
  });

  it('counts a pair carrying both bits', () => {
    expect(enabled(EXT_READ_ALLOW | EXT_READ_DENY)).toEqual([true, false, false]);
  });

  it('does not let one permission leak into another', () => {
    const writeAndExecute =
      EXT_WRITE_ALLOW | EXT_WRITE_DENY | EXT_EXECUTE_ALLOW | EXT_EXECUTE_DENY;
    expect(enabled(writeAndExecute)).toEqual([false, true, true]);
  });

  it('returns booleans rather than the bits themselves', () => {
    expect(readPermissionEnabled(EXT_READ_ALLOW)).toBe(true);
    expect(readPermissionEnabled(0)).toBe(false);
  });

  it('reports every permission on a mask that mentions them all', () => {
    const all =
      EXT_READ_ALLOW | EXT_READ_DENY |
      EXT_WRITE_ALLOW | EXT_WRITE_DENY |
      EXT_EXECUTE_ALLOW | EXT_EXECUTE_DENY;
    expect(enabled(all)).toEqual([true, true, true]);
  });
});
