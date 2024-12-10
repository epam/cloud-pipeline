import { AclClass, UserInfo } from '@cloud-pipeline/core';
import type { Meta, StoryObj } from '@storybook/react';

import { UserCard } from '../../lib/index';

const testUser: UserInfo = {
  id: 1,
  owner: 'ONWER',
  name: 'userName',
  aclClass: AclClass.user,
  roles: [],
  groups: [],
  mask: 15,
  attributes: {
    firstName: 'Powder',
    lastName: 'Jinx',
    email: 'powder@jinx.com',
  },
};

const meta: Meta<typeof UserCard> = {
  title: 'Components/UserCard',
  component: UserCard,
  // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
  // tags: ['autodocs'],
  args: {
    user: testUser,
    showIcon: true,
  },
  parameters: {
    // More on how to position stories at: https://storybook.js.org/docs/configure/story-layout
    layout: 'fullscreen',
  },
};

export const Default: StoryObj<typeof UserCard> = {};

export default meta;
