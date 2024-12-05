export enum ProjectFilter {
  OWNER = 'owner',
}

const allowedTags: { id: string; label: string }[] = [
  { id: 'project-type', label: 'Type' },
  { id: 'ProjectID', label: 'ID' },
  { id: 'ngs-data-location', label: 'Data Location' },
];
const deniedTags: string[] = ['ngs-data-location'];

export const projectTagsToDisplay = allowedTags.filter(
  (tag) => !deniedTags.includes(tag.id),
);
