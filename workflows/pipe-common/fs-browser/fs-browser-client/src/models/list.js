import Remote from './base';

const def = path => [
  {
    name: 'file_a.txt',
    path: path ? `${path}/file_a.txt` : 'file_a.txt',
    type: 'File',
    size: 12,
  },
  {
    name: 'file_b.txt',
    path: path ? `${path}/file_b.txt` : 'file_b.txt',
    type: 'File',
    size: 123232,
  },
  {
    name: 'file_a.txt',
    path: path ? `${path}/file_c.txt` : 'file_c.txt',
    type: 'File',
    size: 14242352872,
  },
  {
    name: 'Folder A',
    path: path ? `${path}/Folder A` : 'Folder A',
    type: 'Folder',
  },
  {
    name: 'Folder B',
    path: path ? `${path}/Folder B` : 'Folder B',
    type: 'Folder',
  },
];

export default class List extends Remote {
  constructor(path) {
    super(`LIST_${path || ''}`, def(path));
    this.url = `/view/${path}`;
  }
}
