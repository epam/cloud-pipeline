const quotesRegExp = /^"([^"]+)"$/;

function clearQuotes (string) {
  if (string) {
    const result = quotesRegExp.exec(string);
    if (result && result.length > 1) {
      return result[1];
    }
  }
  return string;
}

function testQuotes (string) {
  if (string) {
    const result = quotesRegExp.exec(string);
    if (result && result.length > 1) {
      return {
        string: result[1],
        useQuotes: true
      };
    }
  }
  return {
    string,
    useQuotes: false
  };
}

function quotedStringsAreEqual (stringA, stringB) {
  return clearQuotes(stringA) === clearQuotes(stringB);
}

function wrapInQuotes (string) {
  if (!string) {
    return string;
  }
  return `"${clearQuotes(string)}"`;
}

export default {
  clear: clearQuotes,
  equals: quotedStringsAreEqual,
  test: testQuotes,
  wrap: wrapInQuotes
};
