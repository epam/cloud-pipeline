import type { FunctionComponent, ReactNode } from 'react';
import { CommonProps } from '../common.types.ts';

export type MarkdownTagRendererPropsMapper<P extends CommonProps> = (
  props: Record<string, unknown>,
) => P;

export type MarkdownTagRenderer<P extends CommonProps = CommonProps> = {
  tag: string;
  renderer: FunctionComponent<P>;
  propsMapper?: MarkdownTagRendererPropsMapper<P>;
};

export type MarkdownProps = CommonProps & {
  children: ReactNode;
};
