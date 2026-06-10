import type {MetadataValueRendererProps} from './types.ts';

function SecretRenderer(props: MetadataValueRendererProps) {
  const {className, style} = props;
  return (
    <span className={className} style={style}>
      *****
    </span>
  );
}

export {SecretRenderer};
