import type { EditedTag, ValidatedTag } from './types.ts';

let tagKeyIdx = 0;

export function generateUniqueTagKey() {
  tagKeyIdx += 1;
  return `___TEMPORARY__TAG__KEY__${tagKeyIdx}__${new Date().toISOString()}___`;
}

export function validateTag(
  tag: EditedTag,
  _: number,
  tags: EditedTag[],
  restrictedTags: string[],
): ValidatedTag {
  let validationError: string | undefined;
  if (tag.key.trim().length === 0) {
    validationError = 'Tag name is required';
  } else if (restrictedTags.map((rt) => rt.toLowerCase()).includes(tag.key.trim().toLowerCase())) {
    validationError = 'This tag name is reserved';
  } else if (
    tags.filter(
      (t) => t.key.trim().toLowerCase() === tag.key.trim().toLowerCase(),
    ).length > 1
  ) {
    validationError = 'Tag name should be unique';
  }
  return {
    ...tag,
    validationError,
  };
}

export function generateTagValidation(
  restrictedTags: string[],
): (tag: EditedTag, _: number, tags: EditedTag[]) => ValidatedTag {
  return (tag: EditedTag, _: number, tags: EditedTag[]) =>
    validateTag(tag, _, tags, restrictedTags);
}
