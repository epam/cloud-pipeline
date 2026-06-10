import {NavLink, Outlet, useParams} from 'react-router-dom';

const versionSections = [
  {key: 'history', label: 'History'},
  {key: 'code', label: 'Code'},
  {key: 'configuration', label: 'Configuration'},
  {key: 'graph', label: 'Graph'},
  {key: 'workflow', label: 'Workflow'},
  {key: 'documents', label: 'Documents'},
  {key: 'storage', label: 'Storage rules'},
] as const;

function PipelineVersionLayout() {
  const {id, version} = useParams<{id: string; version: string}>();

  return (
    <div className="flex h-full flex-col">
      <header className="border-b border-neutral-200 px-4 py-3">
        <h1 className="text-lg font-semibold">
          Pipeline {id} / {version}
        </h1>
      </header>
      <nav className="border-b border-neutral-200 px-4 py-2">
        <ul className="flex flex-wrap gap-2">
          {versionSections.map((section) => (
            <li key={section.key}>
              <NavLink
                className={({isActive}) =>
                  [
                    'rounded px-3 py-1.5 text-sm',
                    isActive
                      ? 'bg-neutral-900 text-white'
                      : 'text-neutral-600 hover:bg-neutral-100',
                  ].join(' ')
                }
                to={`/${id}/${version}/${section.key}`}
              >
                {section.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <section className="min-h-0 flex-1 overflow-auto">
        <Outlet />
      </section>
    </div>
  );
}

export {PipelineVersionLayout};
