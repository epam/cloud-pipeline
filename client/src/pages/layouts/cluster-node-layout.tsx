import {NavLink, Outlet, useParams} from 'react-router-dom';

const sections = [
  {key: 'info', label: 'General info'},
  {key: 'jobs', label: 'Jobs'},
  {key: 'monitor', label: 'Monitor'},
] as const;

function ClusterNodeLayout() {
  const {nodeName} = useParams<{nodeName: string}>();

  return (
    <div className="flex h-full flex-col">
      <header className="border-b border-neutral-200 px-4 py-3">
        <h1 className="text-lg font-semibold">Node {nodeName}</h1>
      </header>
      <nav className="border-b border-neutral-200 px-4 py-2">
        <ul className="flex gap-2">
          {sections.map((section) => (
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
                to={`/cluster/${nodeName}/${section.key}`}
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

export {ClusterNodeLayout};
