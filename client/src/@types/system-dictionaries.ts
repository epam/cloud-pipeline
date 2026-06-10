export type SystemDictionaryLink = {
  key?: string;
  value?: string;
};

export type SystemDictionaryValue = {
  value?: string;
  links?: SystemDictionaryLink[];
};

export type SystemDictionary = {
  key?: string;
  values?: SystemDictionaryValue[];
};
