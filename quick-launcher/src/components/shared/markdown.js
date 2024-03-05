import React, {useEffect, useState} from 'react';
import showdown from 'showdown';
import './markdown.css';

const converter = new showdown.Converter();

function Markdown(
  {
    className,
    children,
    src,
    forwardedRef,
    style
  }
) {
  const text = typeof children === 'string' ? children : undefined;
  const [source, setSource] = useState(text || '');
  useEffect(() => {
    if (src) {
      fetch(src)
        .then(response => {
          return response.text();
        })
        .then(setSource)
        .catch(() => {});
    }
  }, [src]);
  return (
    <div
      ref={forwardedRef}
      className={[className, 'markdown'].filter(Boolean).join(' ')}
      dangerouslySetInnerHTML={{
        __html: converter.makeHtml(source)
      }}
      style={style}
    >
    </div>
  );
}

export default React.forwardRef((props, ref) => (
  <Markdown {...props} forwardedRef={ref} />
));
