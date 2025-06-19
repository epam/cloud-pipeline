from cp_ai.common import cp_ai_settings
from llama_index.core import Settings
from llama_index.core.types import ChatMessage
from llama_index.llms.google_genai import GoogleGenAI
from llama_index.embeddings.google_genai import GoogleGenAIEmbedding


llm = GoogleGenAI(model=cp_ai_settings.GOOGLE_GENAI_MODEL)
embed_model = GoogleGenAIEmbedding(
    model_name=cp_ai_settings.EMBED_MODEL_NAME,
    embed_batch_size=100
)
Settings.embed_model = embed_model


def llm_simple_query(query: str) -> str:
    return llm.chat(messages=[ChatMessage(content=query)]).message.content
