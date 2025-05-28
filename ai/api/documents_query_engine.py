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
REPO = "cloud-pipeline"
REPO_DOCUMENTS_FOLDER = "docs/md"
BRANCH = "develop"
DOCUMENTS_FOLDER = "data"
DOCUMENTS_COLLECTION_NAME = "Documents"

llm = GoogleGenAI(model=os.environ["GOOGLE_GENAI_MODEL"])
github_token = os.environ["GITHUB_TOKEN"]

def create_index(collection_name):
    _set_up_embed_model()
    issues = _get_issues()
    documents = _issues_to_docs(issues)

    documents_folder = _clone_documents()
    docs = SimpleDirectoryReader(documents_folder, recursive=True).load_data()
    for doc in docs:
        doc.metadata[SOURCE_METADATA] = _get_document_url(doc)
    documents.extend(docs)

    if documents:
        chroma_client = chromadb.PersistentClient()
        chroma_collection = chroma_client.create_collection(collection_name)
        vector_store = ChromaVectorStore(chroma_collection=chroma_collection)
        storage_context = StorageContext.from_defaults(vector_store=vector_store)
        return VectorStoreIndex.from_documents(documents, storage_context=storage_context)
    else:
        print("Index was not created. No documents found.")

def get_query_engine():
    _set_up_embed_model()
    collection_name = DOCUMENTS_COLLECTION_NAME
    try:
        chroma_client = chromadb.PersistentClient()
        chroma_collection = chroma_client.get_collection(collection_name)
        vector_store = ChromaVectorStore(chroma_collection=chroma_collection)
        index = VectorStoreIndex.from_vector_store(vector_store=vector_store)
    except NotFoundError:
        print(f"There is no {collection_name} collection. Llama index for documents should be created in Chroma DB.")
        index = create_index(collection_name)
    return index.as_query_engine(llm=llm)

def main():
    query_engine = get_query_engine()
    response = query_engine.query(
        "When initial vulnerability scan is expected to run automatically?"
    )
    print(response)


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

def _get_document_url(doc):
    path = doc.metadata['file_path'] \
        .replace(os.getcwd(), "") \
        .replace(os.path.sep, "/") \
        .replace(f"/{DOCUMENTS_FOLDER}/", "")
    return f"https://github.com/{REPO_OWNER}/{REPO}/tree/{BRANCH}/{REPO_DOCUMENTS_FOLDER}/{path}"

def _set_up_embed_model():
    embed_model = GoogleGenAIEmbedding(
        model_name=EMBED_MODEL_NAME,
        embed_batch_size=100
    )
    Settings.embed_model = embed_model

if __name__ == '__main__':
    main()
