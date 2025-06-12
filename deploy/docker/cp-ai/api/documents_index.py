import os.path
import requests
import zipfile
import io
import chromadb
from chromadb.errors import NotFoundError
from llama_index.llms.google_genai import GoogleGenAI
from llama_index.core import VectorStoreIndex, SimpleDirectoryReader, StorageContext, Settings, Document, load_index_from_storage
from llama_index.embeddings.google_genai import GoogleGenAIEmbedding
from llama_index.vector_stores.chroma import ChromaVectorStore

EMBED_MODEL_NAME = "text-embedding-004"
SOURCE_METADATA = "source"

REPO_OWNER = "epam"
APPLICATION_URI = "https://aws.cloud-pipeline.com/pipeline/#"
REPO = "cloud-pipeline"
REPO_DOCUMENTS_FOLDER = "docs/md"
BRANCH = "develop"
DOCUMENTS_COLLECTION_NAME = "Documents"

llm = GoogleGenAI(model=os.environ["GOOGLE_GENAI_MODEL"])
github_token = os.environ["GITHUB_TOKEN"]
chroma_db_path = os.environ["CHROMA_DB_PATH"]
links = {
    "tool": "/tools",
    "pipeline": "/pipelines",
    "storage": "/storages",
    "dashboard": "/dashboard",
    "active runs": "/runs/active",
    "pausing runs": "/runs/pausing",
    "paused runs": "/runs/paused",
    "resuming runs": "/runs/resuming",
    "completed runs": "/runs/completed",
    "settings": "/settings/cli"
}

def create_index():
    chroma_client = chromadb.PersistentClient(path=chroma_db_path)
    collections = chroma_client.list_collections()
    if any(c == DOCUMENTS_COLLECTION_NAME for c in collections):
        return

    _set_up_embed_model()
    issues = _get_issues()
    print(f"Issues: {len(issues)}.")
    documents = _issues_to_docs(issues)

    documents_folder = _clone_documents()
    docs = SimpleDirectoryReader(documents_folder, recursive=True).load_data()
    for doc in docs:
        doc.metadata[SOURCE_METADATA] = _get_document_url(doc, documents_folder)
    documents.extend(docs)
    print(f"Documents: {len(documents)}.")

    if not documents:
        print("Index was not created. No documents found.")
        return

    chroma_collection = chroma_client.create_collection(DOCUMENTS_COLLECTION_NAME)
    vector_store = ChromaVectorStore(chroma_collection=chroma_collection)
    storage_context = StorageContext.from_defaults(vector_store=vector_store)
    VectorStoreIndex.from_documents(documents, storage_context=storage_context)

def get_link(query: str, response: str) -> str:
    prompt = f"""Given the following question:
    {query}
    And the following answer:
    {response}
    Choose the best link to use from this list: [{links}]. 
    If nothing suitable, return None.
    Only reply with the link."""
    link = llm.complete(prompt).text.strip()
    if link != "None":
        link = f''' Application Link:{APPLICATION_URI}{link}.'''
    else:
        link = ""
    return link

def query_documents(query: str) -> str:
    query_engine = _get_query_engine()
    response = query_engine.query(query)
    link = get_link(query, str(response))
    sources = ";".join(set([s.metadata["source"] for s in response.source_nodes]))
    result = f'''Result: <<<{response}. Sources:{sources}.{link}>>>. 
    Include this result into response to user. 
    IMPORTANT!!!: Always include Sources!.'''
    return result

def _get_query_engine():
    _set_up_embed_model()
    try:
        chroma_client = chromadb.PersistentClient(path=chroma_db_path)
        chroma_collection = chroma_client.get_collection(DOCUMENTS_COLLECTION_NAME)
        vector_store = ChromaVectorStore(chroma_collection=chroma_collection)
        index = VectorStoreIndex.from_vector_store(vector_store=vector_store)
        return index.as_query_engine(llm=llm)
    except NotFoundError:
        print(f"There is no {DOCUMENTS_COLLECTION_NAME} collection. Llama index for documents should be created in Chroma DB.")
        return None

def _get_issues():
    import requests

    headers = {"Authorization": f"Bearer {github_token}"}
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
    """ % (REPO_OWNER, REPO)

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
        doc = Document(
            text="%s. %s".format(issue.get("title"), issue.get("body")),
            metadata={
                SOURCE_METADATA: issue.get("url")
            }
        )
        docs.append(doc)
    return docs

def _clone_documents():
    zip_url = f"https://github.com/{REPO_OWNER}/{REPO}/archive/refs/heads/{BRANCH}.zip"
    r = requests.get(zip_url)
    z = zipfile.ZipFile(io.BytesIO(r.content))
    z.extractall("/tmp/repo")
    return f"/tmp/repo/{REPO}-{BRANCH}/{REPO_DOCUMENTS_FOLDER}"

def _get_document_url(doc, documents_folder):
    path = ((doc.metadata['file_path']
            .replace(os.path.abspath(documents_folder), ""))
            .replace(os.path.sep, "/"))
    return f"https://github.com/{REPO_OWNER}/{REPO}/tree/{BRANCH}/{REPO_DOCUMENTS_FOLDER}{path}"

def _set_up_embed_model():
    embed_model = GoogleGenAIEmbedding(
        model_name=EMBED_MODEL_NAME,
        embed_batch_size=100
    )
    Settings.embed_model = embed_model

def main():
    query_engine = _get_query_engine()
    response = query_engine.query(
        "When initial vulnerability scan is expected to run automatically?"
    )
    print(response)

if __name__ == '__main__':
    main()
