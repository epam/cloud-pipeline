import type {MappedTag} from "../../../../shared/tags";

export type EditedTag = MappedTag & {
  initialKey: string;
  initialValue: MappedTag['value'];
  hidden: boolean;
  removed: boolean;
  isNewTag: boolean;
  readonlyKey: boolean;
  removable: boolean;
};

export type ValidatedTag = EditedTag & {
  validationError?: string;
};