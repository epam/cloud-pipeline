import HtmlRenderer from './html-renderer';
import ImageRenderer from './image-renderer';
import MiewRenderer from './miew-renderer';
import PlainTextRenderer from './plain-text-renderer';
import TabularDataRenderer from './tabular-renderer';

export const renderers = [
  PlainTextRenderer,
  HtmlRenderer,
  TabularDataRenderer,
  ImageRenderer,
  MiewRenderer,
];

export {PlainTextRenderer, HtmlRenderer, TabularDataRenderer, ImageRenderer, MiewRenderer};
