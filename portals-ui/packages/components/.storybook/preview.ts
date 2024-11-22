import type { Preview } from '@storybook/react';
import { withApplication } from './with-application';

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
  decorators: [withApplication],
};

export default preview;
