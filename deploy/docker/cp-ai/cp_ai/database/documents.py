import os.path
import requests
import zipfile
import io
import chromadb
from logging import Logger
from chromadb.errors import NotFoundError
from llama_index.core import VectorStoreIndex, SimpleDirectoryReader, StorageContext, Document
from llama_index.core.schema import NodeWithScore
from llama_index.vector_stores.chroma import ChromaVectorStore

from cp_ai.common import cp_ai_settings
from cp_ai.common.logger import create_logger
from cp_ai.common.utilities import remove_quotes
from cp_ai.llm import embed_model, llm, llm_simple_query

documents_logger = create_logger('documents', cp_ai_settings.cp_documents_logs_file)

SOURCE_METADATA = "source"
TITLE_METADATA = "file_name"
DOCUMENTS_COLLECTION_NAME = "Documents"

links = {
    "tool": "#/tools",
    "pipeline": "#/pipelines",
    "storage": "#/storages",
    "dashboard": "#/dashboard",
    "active runs": "#/runs/active",
    "pausing runs": "#/runs/pausing",
    "paused runs": "#/runs/paused",
    "resuming runs": "#/runs/resuming",
    "completed runs": "#/runs/completed",
    "settings": "#/settings"
}

def create_index(logger: Logger | None = None):
    if logger is None:
        logger = documents_logger
    if cp_ai_settings.CHROMA_DB_PATH is None:
        raise RuntimeError('CHROMA_DB_PATH is not specified')
    chroma_client = chromadb.PersistentClient(path=cp_ai_settings.CHROMA_DB_PATH)
    collections = chroma_client.list_collections()
    if any(c == DOCUMENTS_COLLECTION_NAME for c in collections):
        logger.info('documents db already created')
        return

    logger.info('creating documents db')
    logger.info('reading issues...')
    issues = _get_issues()
    logger.info(f'issues: {len(issues)}.')
    documents = _issues_to_docs(issues)
    logger.info('cloning documents...')
    documents_folder = _clone_documents()
    logger.info(f'documents cloned: {documents_folder}')
    logger.info('reading documents...')
    # we're NOT excluding hidden files, because:
    # - we don't have them within the cloud pipeline repo's docs/md folder
    # - llama_index's SimpleDirectoryReader treats all paths with dot at the beginning (".xxx") as a hidden paths,
    #   and for temp folders like ".tmp/repo" it will consider ALL files as hidden
    docs = SimpleDirectoryReader(documents_folder,
                                 recursive=True,
                                 exclude_hidden=False).load_data()
    logger.info(f'documents: {len(docs)} documents read')
    for doc in docs:
        doc.metadata[TITLE_METADATA] = doc.metadata.get('file_name', 'No title')
        doc.metadata[SOURCE_METADATA] = _get_document_url(doc, documents_folder)
    documents.extend(docs)

    if not documents:
        logger.info('documents index was not created. no documents found.')
        return

    logger.info(f'saving {len(documents)} issues and documents...')
    chroma_collection = chroma_client.create_collection(DOCUMENTS_COLLECTION_NAME)
    vector_store = ChromaVectorStore(chroma_collection=chroma_collection)
    storage_context = StorageContext.from_defaults(vector_store=vector_store)
    VectorStoreIndex.from_documents(documents,
                                    storage_context=storage_context,
                                    embed_model=embed_model)
    logger.info(f'documents and issues processed, index created')

def get_application_link(query: str, response: str) -> str:
    prompt = f"""Given the following question:
    {query}
    And the following answer:
    {response}
    Choose the best link to use from this list: [{links}]. 
    If nothing suitable, return None.
    Only reply with the link."""
    link = llm.complete(prompt).text.strip()
    if link != "None":
        link = f'''\n\nApplication Link: {cp_ai_settings.CP_APPLICATION_URI}{link}.'''
    else:
        link = ""
    return link


def _node_to_md(node: NodeWithScore) -> str:
    title = node.metadata.get(TITLE_METADATA, None)
    source = node.metadata.get(SOURCE_METADATA, None)
    if title is not None and source is not None:
        return f'[{title}]({source})'
    if title is not None:
        return str(title)
    if source is not None:
        return str(source)
    return node.node_id


def query_documents(query: str, **kwargs) -> str:
    logger = kwargs.get('logger', None)
    if not isinstance(logger, Logger):
        logger = None
    query_engine = _get_query_engine(logger=logger)
    response = query_engine.query(query)
    link = get_application_link(query, str(response))
    sources = "\n".join(set([f'- {_node_to_md(s)}' for s in response.source_nodes]))
    return (f'{response}\n\n'
            f'{link}\n\n'
            f'**References**:\n'
            f'{sources}')


def search_platform_documentation(
        query: str,
        user_query: str | None = None,
        **kwargs
) -> str:
    """Useful for answering user questions / retrieving information from the platform documentation.
    Use this tool for questions like "How can I...", "What is...", "Where can I..." and other general questions.
    Required parameters:
    - query, string - a query to be used to search documentation / issues
    Optional parameters:
    - user_query, string, optional - original user query or question (as is)
    """
    logger = kwargs.get('logger', None)
    if not isinstance(logger, Logger):
        logger = None
    if query is None:
        query = user_query
    if user_query is None:
        user_query = query
    if query is None or user_query is None:
        raise RuntimeError('please specify the question / query')
    query_engine = _get_query_engine(logger=logger)
    response = query_engine.query(query)

    def process_single_node(_node: NodeWithScore):
        title = _node_to_md(_node)
        base_instruction = ''
        source = node.metadata.get(SOURCE_METADATA, None)
        if source is not None:
            base_instruction = f'- Change all relative links to absolute links, the base is "{source}".\n'
        prompt = (f'Here\'s the context:\n'
                  f'--------------\n'
                  f'{title}\n'
                  f'{_node.text}\n\n'
                  f'--------------\n'
                  f'Summarize the context, trying to answer the user query:\n'
                  f'--------------\n'
                  f'{user_query}\n'
                  f'--------------\n'
                  f'\n'
                  f'- Respond exactly "NOT RELEVANT", if the context is not relevant and not answers user query.\n'
                  f'- Include all links and references to the final response, if it is relevant; use markdown format.\n'
                  f'{base_instruction}'
                  f'- For images, use ![<image name>](<url> "image name") format.\n'
                  f'- Do not use words like "in the provided context...", act as you\'re answering a user query.\n')
        node_response = llm_simple_query(prompt).strip()
        if remove_quotes(node_response).lower() in {'not relevant', 'not_relevant', 'not-relevant'}:
            return ''
        return node_response

    results = []

    for node in response.source_nodes[:20]:
        n_r = process_single_node(node)
        if n_r:
            results.append({'text': n_r, 'link': _node_to_md(node)})

    if len(results) > 0:
        result = '\n\n'.join([r.get('text') for r in results])
        result_links = list(set([r.get('link') for r in results]))
        links_result = '\n'.join([f'- {l}' for l in result_links])
        return (f'{result}\n'
                f'\n'
                f'**References:**\n\n'
                f'{links_result}')

    return 'Nothing found'


def _get_query_engine(logger: Logger | None = None):
    if logger is None:
        logger = documents_logger
    try:
        if cp_ai_settings.CHROMA_DB_PATH is None:
            raise RuntimeError('CHROMA_DB_PATH is not specified')
        chroma_client = chromadb.PersistentClient(path=cp_ai_settings.CHROMA_DB_PATH)
        chroma_collection = chroma_client.get_collection(DOCUMENTS_COLLECTION_NAME)
        vector_store = ChromaVectorStore(chroma_collection=chroma_collection)
        index = VectorStoreIndex.from_vector_store(vector_store=vector_store,
                                                   embed_model=embed_model)
        return index.as_query_engine(llm=llm)
    except NotFoundError:
        logger.error(
            f'There is no {DOCUMENTS_COLLECTION_NAME} collection. '
            f'Llama index for documents should be created in Chroma DB.'
        )
    except BaseException as e:
        logger.error('error querying documents',
                     exc_info=e)
    return None


def _get_issues():
    headers = {}
    if cp_ai_settings.GITHUB_TOKEN is not None:
        headers.update({"Authorization": f"Bearer {cp_ai_settings.GITHUB_TOKEN}"})
    graphql_url = "https://api.github.com/graphql"

    query = """
    query($cursor: String) {
      repository(owner: "%s", name: "%s") {
        issues(first: 100, after: $cursor) {
          pageInfo {
            hasNextPage
            endCursor
          }
          nodes {
            number
            title
            body
            url
          }
        }
      }
    }
    """ % (cp_ai_settings.GITHUB_REPO_OWNER, cp_ai_settings.GITHUB_REPO)

    cursor = None
    all_issues = []

    while True:
        variables = {"cursor": cursor}
        response = requests.post(graphql_url, json={"query": query, "variables": variables}, headers=headers)
        data = response.json()

        if "errors" in data:
            print("Error:", data["errors"])
            break

        issues = data["data"]["repository"]["issues"]["nodes"]
        all_issues.extend(issues)

        page_info = data["data"]["repository"]["issues"]["pageInfo"]
        if not page_info["hasNextPage"]:
            break
        cursor = page_info["endCursor"]
    return all_issues

def _issues_to_docs(issues):
    docs = []
    for issue in issues:
        title = issue.get('title', 'No title')
        body = issue.get('body')
        if body is not None:
            doc = Document(
                text=f'{title}. {body}',
                metadata={
                    TITLE_METADATA: title,
                    SOURCE_METADATA: issue.get("url")
                }
            )
            docs.append(doc)
    return docs

def _clone_documents():
    zip_url = (f"https://github.com"
               f"/{cp_ai_settings.GITHUB_REPO_OWNER}"
               f"/{cp_ai_settings.GITHUB_REPO}"
               f"/archive/refs/heads"
               f"/{cp_ai_settings.GITHUB_REPO_BRANCH}.zip")
    r = requests.get(zip_url)
    z = zipfile.ZipFile(io.BytesIO(r.content))

    tmp_path = os.path.abspath(cp_ai_settings.GITHUB_CLONE_TMP_PATH)
    z.extractall(tmp_path)
    return (f"{tmp_path}"
            f"/{cp_ai_settings.GITHUB_REPO}-{cp_ai_settings.GITHUB_REPO_BRANCH}"
            f"/{cp_ai_settings.GITHUB_REPO_DOCS_PATH}")

def _get_document_url(doc, documents_folder):
    path = ((doc.metadata['file_path']
            .replace(os.path.abspath(documents_folder), ""))
            .replace(os.path.sep, "/"))
    if not path.startswith('/'):
        path = '/' + path
    return (f"https://github.com"
            f"/{cp_ai_settings.GITHUB_REPO_OWNER}"
            f"/{cp_ai_settings.GITHUB_REPO}"
            f"/tree"
            f"/{cp_ai_settings.GITHUB_REPO_BRANCH}"
            f"/{cp_ai_settings.GITHUB_REPO_DOCS_PATH}"
            f"{path}")
