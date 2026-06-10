import type {LibraryAclClass, LibraryRootFolder} from '../../@types/library.ts';
import type {Folder} from '../../@types/library.ts';
import type {MetadataEntity} from '../../@types/metadata.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function loadFolderTree(cached?: number | boolean): Promise<LibraryRootFolder> {
  return cloudPipelineApi.jsonGet<LibraryRootFolder>({uri: 'folder/loadTree', cached});
}

export async function loadFolder(id: number): Promise<Folder> {
  return cloudPipelineApi.jsonGet<Folder>({uri: `folder/${id}/load`});
}

export async function findFolder(identifier: string): Promise<Folder> {
  return cloudPipelineApi.jsonGet<Folder>({uri: 'folder/find', query: {id: identifier}});
}

export async function loadProjects(): Promise<Folder> {
  return cloudPipelineApi.jsonGet<Folder>({uri: 'folder/projects'});
}

export async function loadProject(id: number, aclClass: LibraryAclClass): Promise<Folder> {
  return cloudPipelineApi.jsonGet<Folder>({uri: 'folder/project', query: {id, aclClass}});
}

export async function registerFolder(folder: Folder, templateName?: string): Promise<Folder> {
  return cloudPipelineApi.jsonPost<Folder>({
    uri: 'folder/register',
    body: folder,
    query: templateName ? {templateName} : undefined,
  });
}

export async function updateFolder(
  folder: Pick<Folder, 'id' | 'name' | 'parentId'>,
): Promise<Folder> {
  return cloudPipelineApi.jsonPost<Folder>({uri: 'folder/update', body: folder});
}

export async function deleteFolder(id: number, force = false): Promise<Folder> {
  return cloudPipelineApi.jsonDelete<Folder>({
    uri: `folder/${id}/delete`,
    query: {force},
  });
}

export async function loadFolderMetadataEntities(
  id: number,
  className: string,
): Promise<MetadataEntity[]> {
  return cloudPipelineApi.jsonGet<MetadataEntity[]>({
    uri: `folder/${id}/metadata`,
    query: {class: className},
  });
}

export async function cloneFolder(id: number, name: string, parentId?: number): Promise<Folder> {
  return cloudPipelineApi.jsonPost<Folder>({
    uri: `folder/${id}/clone`,
    query: {name, parentId},
  });
}

export async function lockFolder(id: number): Promise<Folder> {
  return cloudPipelineApi.jsonPost<Folder>({uri: `folder/${id}/lock`});
}

export async function unlockFolder(id: number): Promise<Folder> {
  return cloudPipelineApi.jsonPost<Folder>({uri: `folder/${id}/unlock`});
}
