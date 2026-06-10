import type {ReactNode} from 'react';

type PagePlaceholderProps = {
  title: string;
  description?: string;
  children?: ReactNode;
};

function PagePlaceholder({title, description, children}: PagePlaceholderProps) {
  return (
    <div className="flex h-full flex-col gap-4 p-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold">{title}</h1>
        {description ? <p className="text-sm text-neutral-500">{description}</p> : null}
      </header>
      {children ?? (
        <div className="rounded-lg border border-dashed border-neutral-300 p-8 text-neutral-500">
          Not implemented yet
        </div>
      )}
    </div>
  );
}

export {PagePlaceholder};
