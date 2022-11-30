import { useState } from 'react';

function generator() {
  let key = 0;
  return function generateUniqueKey() {
    key += 1;
    return `key-${key}`;
  };
}

const generateUniqueKey = generator();

function useSimpleUniqueKey() {
  return useState(generateUniqueKey())[0];
}

export { generateUniqueKey, useSimpleUniqueKey };
