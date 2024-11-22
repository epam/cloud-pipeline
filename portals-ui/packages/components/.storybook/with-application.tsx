import Application from './application';
import type { StoryFn } from '@storybook/react';

export const withApplication = (Story: StoryFn) => {
  return (
    <Application style={{ padding: 10 }}>
      <Story />
    </Application>
  );
};
