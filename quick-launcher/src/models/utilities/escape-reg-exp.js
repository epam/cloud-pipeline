const ESCAPE_CHARACTERS = ['.', '-', '*', '?', '^', '$', '(', ')', '[', ']', '{', '}'];

function escapeRegExp (string, characters = ESCAPE_CHARACTERS) {
  let result = string;
  characters.forEach(character => {
    result = result
      .replace(new RegExp('\\' + character, 'g'), `\\${character}`)
  });
  return result;
}

export {escapeRegExp, ESCAPE_CHARACTERS};
