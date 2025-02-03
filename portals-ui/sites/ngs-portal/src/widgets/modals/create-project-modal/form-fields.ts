export type CreateProjectFormValues = {
  projectName: string;
  datastorage: number;
};

export enum CreateProjectField {
  ProjectName = 'projectName',
  Datastorage = 'datastorage',
}

export const createProjectFieldConfig = {
  [CreateProjectField.ProjectName]: {
    name: CreateProjectField.ProjectName,
    label: 'Name:',
    rules: [
      { required: true, message: 'Please provide a project name' },
      {
        pattern: /^[a-zA-Z0-9_-]+$/,
        message: 'Only letters, numbers, "_" or "-" are allowed',
      },
    ],
  },
  [CreateProjectField.Datastorage]: {
    name: CreateProjectField.Datastorage,
    label: 'Default datastorage:',
  },
};
