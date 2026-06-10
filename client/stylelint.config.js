export default {
  extends: ['stylelint-config-standard'],
  rules: {
    'selector-pseudo-class-no-unknown': [true, {ignorePseudoClasses: ['global']}],
    // Allow third-party class names (e.g. CodeMirror) and legacy camelCase IDs
    'selector-class-pattern': '^([a-z][a-z0-9-]*(__[a-z0-9-]+)*|CodeMirror-[a-zA-Z-]+)$',
    'selector-id-pattern': '^([a-z]+(-[a-z]+)*|[a-z][a-zA-Z0-9]*)$',
  },
};
