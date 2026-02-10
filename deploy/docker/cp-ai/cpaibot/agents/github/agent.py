import chromadb
import os
import time
from chromadb.errors import NotFoundError
from llama_index.core import VectorStoreIndex
from llama_index.core.schema import NodeWithScore
from llama_index.core.query_engine import BaseQueryEngine
from llama_index.vector_stores.chroma import ChromaVectorStore

from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.settings import settings
from cpaibot.database.github import GITHUB_DOCUMENTS_COLLECTION_NAME
from cpaibot.llm.base import embed_model, llm, llm_chat
from cpaibot.common.model.chat import MessagePart
from cpaibot.managers.chat import update_message_part_from_gen, update_message_part

from google.genai.errors import ClientError


default_logger = Logger("github-agent")


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


def _get_query_engine(logger: Logger | None = None, streaming=False) -> BaseQueryEngine | None:
    if logger is None:
        logger = default_logger
    try:
        if settings.CHROMA_DB_PATH is None:
            raise RuntimeError('CHROMA_DB_PATH is not specified')
        chroma_client = chromadb.PersistentClient(path=settings.CHROMA_DB_PATH)
        chroma_collection = chroma_client.get_collection(GITHUB_DOCUMENTS_COLLECTION_NAME)
        vector_store = ChromaVectorStore(chroma_collection=chroma_collection)
        index = VectorStoreIndex.from_vector_store(vector_store=vector_store,
                                                   embed_model=embed_model)
        return index.as_query_engine(
            llm=llm,
            similarity_top_k=10,
            response_mode="tree_summarize",
            streaming=streaming,
        )
    except NotFoundError:
        logger.error(
            f'There is no {GITHUB_DOCUMENTS_COLLECTION_NAME} collection. '
            f'Llama index for documents should be created in Chroma DB.'
        )
    except BaseException as e:
        logger.error('error querying documents',
                     exception=e)
    return None


def get_application_link(query: str, response: str) -> str:
    prompt = f"""Given the following question:
    {query}
    And the following answer:
    {response}
    Choose the best link to use from this list: [{links}]. 
    If nothing suitable, return None.
    Only reply with the link."""
    link = llm_chat([prompt])
    if link != "None":
        link = f'''\n\nApplication Link: {settings.CP_APPLICATION_URI}{link}.'''
    else:
        link = ""
    return link


def _node_to_md(node: NodeWithScore) -> str:
    title = node.metadata.get("title", None)
    source = node.metadata.get("url", None)
    if title is not None and source is not None:
        return f'[{title}]({source})'
    if title is not None:
        return str(title)
    if source is not None:
        return str(source)
    return node.node_id


def _is_reference_node(node: NodeWithScore) -> bool:
    file_name = node.metadata.get("file_name", None)
    document_type = node.metadata.get("document_type", None)
    if document_type and document_type.lower() == "github issue":
        return True
    if file_name is not None:
        try:
            ext = os.path.splitext(file_name)[1]
            return ext.lower() not in {".png", ".jpg", ".jpeg", ".css", ".js"}
        except:
            pass
    return True


def query_documents(
        query: str,
        message: MessagePart | None = None,
        max_retries: int | None = None,
        retry_delay_seconds: int | None = None,
        **kwargs
) -> str:
    logger = kwargs.get('logger', None)
    if not isinstance(logger, Logger):
        logger = None
    if max_retries is None:
        max_retries = settings.LLM_MAX_RETRIES
    if retry_delay_seconds is None:
        retry_delay_seconds = settings.LLM_RETRY_DELAY_SECONDS

    prev_message_text = message.text if message is not None else ""
    prev_message_warnings = (message.warnings or []) if message is not None else []
    prev_status = message.status if message is not None else None

    response_text = ""
    source_nodes = []

    # ----
    for attempt in range(max_retries):
        try:
            if message is not None:
                message.text = prev_message_text
                message.status = prev_status
                message.warnings = prev_message_warnings
                update_message_part(message)
            query_engine = _get_query_engine(logger=logger, streaming=True)
            streaming_response = query_engine.query(query)

            if message is None:
                response_text = ""
                for text in streaming_response.response_gen:
                    response_text += text
            else:
                update_message_part_from_gen(message, streaming_response.response_gen)
                response_text = message.text or ""

            source_nodes = streaming_response.source_nodes
            break
        except ClientError as e:
            # Check if it's a 429 rate limit error
            if e.code == 429 or e.status == "RESOURCE_EXHAUSTED":
                if message is not None:
                    message.text = prev_message_text
                    message.warnings = prev_message_warnings
                    update_message_part(message)
                if attempt < max_retries - 1:
                    delay = retry_delay_seconds * (2 ** attempt)  # Exponential backoff
                    status = f"Rate limit hit (429). Retrying in {delay}s (attempt {attempt + 1}/{max_retries})..."
                    if message is not None:
                        message.warnings.append(e.__str__())
                        message.status = status
                        update_message_part(message)
                    if logger:
                        logger.warning(status)
                    time.sleep(delay)
                else:
                    if logger:
                        logger.error(f"Rate limit exceeded after {max_retries} attempts")
                    raise  # Re-raise after all retries exhausted
            else:
                # If it's a different error, raise immediately
                raise
    # ----

    response_text = response_text.strip()
    link = get_application_link(query, response_text)
    if link:
        link += "\n\n"

    reference_nodes = [s for s in source_nodes if _is_reference_node(s)]
    if len(reference_nodes) > 0:
        sources = "\n".join(set([f'- {_node_to_md(s)}' for s in reference_nodes]))
        sources_str = f'**References**:\n{sources}'
    else:
        sources_str = ""

    full_response = f'{response_text}\n\n{link}{sources_str}'
    if message:
        message.text = full_response
        message.set_sources([n.metadata for n in source_nodes])
        update_message_part(message)

    return full_response