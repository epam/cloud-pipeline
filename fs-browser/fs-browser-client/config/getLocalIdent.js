const path = require('path');
const kebabCase = require('kebab-case');

module.exports = (context, localIdentName, localName, options) => {
  if (!options.context) {
    options.context = context.options && typeof context.options.context === 'string'
      ? context.options.context
      : context.context;
  }

  localIdentName = localIdentName.replace(/\[local\]/gi, localName);
  const name = kebabCase(path.basename(context.resourcePath).split('.')[0]);
  return localIdentName.replace(/\[name\]/gi, name);
};
