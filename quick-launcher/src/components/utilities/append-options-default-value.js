import appendOptionsValue from './append-options-value';

export default function appendOptionsDefaultValue(result, setting, value = undefined) {
  const appendValue = value === undefined ? setting.default : value;
  appendOptionsValue(result, setting.optionsField, appendValue);
  if (setting.valueHasSubOptions(appendValue) && setting.itemSubOptions) {
    const subOptions = setting.itemSubOptions(appendValue);
    if (subOptions && subOptions.length) {
      appendOptionsValue(
        result.__dependencies__,
        setting.optionsField,
        subOptions
          .map(o => ({[o.key]: o.default}))
          .reduce((r, c) => ({...r, ...c}), {__parent__: appendValue})
      );
      return result;
    }
  }
  appendOptionsValue(
    result.__dependencies__,
    setting.optionsField,
    {__parent__: appendValue}
  );
  return result;
}
