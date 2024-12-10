import type { Meta, StoryObj } from '@storybook/react';

import { List } from '../../lib/index';

type SimpleItem = {
  id: string;
  name: string;
};

const items: SimpleItem[] = [];

for (let i = 0; i < 10000; i++) {
  items.push({
    id: `item-${i}`,
    name: `item-${i}`,
  });
}

const meta: Meta<typeof List<SimpleItem>> = {
  title: 'Components/List',
  component: List<SimpleItem>,
  // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
  // tags: ['autodocs'],
  args: {
    items,
    header: 'Simple items list',
    render(item: SimpleItem) {
      return <span>{item.name}</span>;
    }
  },
  parameters: {
    // More on how to position stories at: https://storybook.js.org/docs/configure/story-layout
    layout: 'fullscreen',
  },
};

export const Default: StoryObj<typeof List> = {};

export default meta;
