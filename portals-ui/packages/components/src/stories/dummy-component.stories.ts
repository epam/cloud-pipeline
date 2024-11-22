import type { Meta, StoryObj } from '@storybook/react';

import { DummyComponent } from '../../lib/index';

const meta = {
  title: 'Components/Dummy Component',
  component: DummyComponent,
  // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
  tags: ['autodocs'],
  parameters: {
    // More on how to position stories at: https://storybook.js.org/docs/configure/story-layout
    layout: 'fullscreen',
  },
} satisfies Meta<typeof DummyComponent>;

export const Default: StoryObj<typeof DummyComponent> = {};

export default meta;
