import time

from llama_index.core.base.llms.types import ChatMessage, MessageRole
from llama_index.llms.google_genai import GoogleGenAI
from llama_index.embeddings.google_genai import GoogleGenAIEmbedding
from cpaibot.common.settings import settings
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.model.chat import Message, MessagePart
from cpaibot.managers.chat import update_message_part, update_message_part_from_gen

from google.genai.errors import ClientError


embed_model = GoogleGenAIEmbedding(
    model_name=settings.EMBED_MODEL_NAME,
    embed_batch_size=100,
    api_url=settings.GOOGLE_API_KEY,
)

llm = GoogleGenAI(
    model_name=settings.DEFAULT_MODEL_NAME,
    api_url=settings.GOOGLE_API_KEY,
)


default_logger = Logger("llm", log_in_cloud_pipeline=False)


def llm_simple_query(query: str) -> str:
    return llm_chat(query)


def normalize_message(mes: str | ChatMessage | Message) -> ChatMessage:
    if isinstance(mes, str):
        return ChatMessage(content=mes, role=MessageRole.USER)
    if isinstance(mes, Message):
        return mes.to_llama_index()
    return mes


def llm_chat(
        messages: str | ChatMessage | Message | list[str | ChatMessage | Message],
        /,
        max_retries: int | None = None,
        retry_delay_seconds: int | None = None,
        part: MessagePart | None = None,
        logger: Logger | None = None,
) -> str:
    if not logger:
        logger = default_logger
    if max_retries is None:
        max_retries = settings.LLM_MAX_RETRIES
    if retry_delay_seconds is None:
        retry_delay_seconds = settings.LLM_RETRY_DELAY_SECONDS

    prev_message_warnings = (part.warnings or []) if part is not None else []
    prev_message_warnings = prev_message_warnings[:]
    prev_status = part.status if part is not None else None

    if isinstance(messages, ChatMessage | Message | str):
        messages = [normalize_message(messages)]
    else:
        messages = [normalize_message(m) for m in messages]

    for attempt in range(max_retries):
        try:
            if part is not None:
                part.status = prev_status
                part.warnings = prev_message_warnings[:]
                update_message_part(part)

            response = llm.chat(messages).message.content
            if part is not None:
                part.status = prev_status
                part.warnings = prev_message_warnings[:]
                update_message_part(part)
            return response
        except ClientError as e:
            # Check if it's a 429 rate limit error
            if e.code == 429 or e.status == "RESOURCE_EXHAUSTED":
                if part is not None:
                    part.warnings = prev_message_warnings[:]
                    update_message_part(part)
                if attempt < max_retries - 1:
                    delay = retry_delay_seconds * (2 ** attempt)  # Exponential backoff
                    status = f"Rate limit hit (429). Retrying in {delay}s (attempt {attempt + 1}/{max_retries})..."
                    if part is not None:
                        part.warnings.append(e.__str__())
                        part.status = status
                        update_message_part(part)
                    logger.warning(status,
                                   exception=e)
                    time.sleep(delay)
                else:
                    logger.error(f"Rate limit exceeded after {max_retries} attempts",
                                 exception=e)
                    raise  # Re-raise after all retries exhausted
            else:
                # If it's a different error, raise immediately
                logger.error(f"an error occurred during llm.chat call",
                             exception=e)
                raise


def llm_stream_chat(
        messages: str | ChatMessage | Message | list[str | ChatMessage | Message],
        /,
        max_retries: int | None = None,
        retry_delay_seconds: int | None = None,
        part: MessagePart | None = None,
        logger: Logger | None = None,
        stream_to_message: bool | None = None,
):
    if not logger:
        logger = default_logger
    if max_retries is None:
        max_retries = settings.LLM_MAX_RETRIES
    if retry_delay_seconds is None:
        retry_delay_seconds = settings.LLM_RETRY_DELAY_SECONDS
    if stream_to_message is None:
        stream_to_message = part is not None

    if isinstance(messages, ChatMessage | Message | str):
        messages = [normalize_message(messages)]
    else:
        messages = [normalize_message(m) for m in messages]

    prev_message_warnings = (part.warnings or []) if part is not None else []
    prev_message_warnings = prev_message_warnings[:]
    prev_status = part.status if part is not None else None
    prev_text = part.text if part is not None else None
    if prev_text is None:
        prev_text = ""

    for attempt in range(max_retries):
        try:
            if part is not None:
                part.status = prev_status
                part.warnings = prev_message_warnings[:]
                if stream_to_message:
                    part.text = prev_text
                update_message_part(part)

            response = llm.stream_chat(messages)
            if stream_to_message and part:
                part.status = prev_status
                part.warnings = prev_message_warnings[:]
                update_message_part_from_gen(part, response)
                return
            if part is not None:
                part.status = prev_status
                part.warnings = prev_message_warnings[:]
                update_message_part(part)
            return response
        except ClientError as e:
            # Check if it's a 429 rate limit error
            if e.code == 429 or e.status == "RESOURCE_EXHAUSTED":
                if part is not None:
                    part.warnings = prev_message_warnings[:]
                    if stream_to_message:
                        part.text = prev_text
                    update_message_part(part)
                if attempt < max_retries - 1:
                    delay = retry_delay_seconds * (2 ** attempt)  # Exponential backoff
                    status = f"Rate limit hit (429). Retrying in {delay}s (attempt {attempt + 1}/{max_retries})..."
                    if part is not None:
                        part.warnings.append(e.__str__())
                        part.status = status
                        if stream_to_message:
                            part.text = prev_text
                        update_message_part(part)
                    logger.warning(status,
                                   exception=e)
                    time.sleep(delay)
                else:
                    logger.error(f"Rate limit exceeded after {max_retries} attempts",
                                 exception=e)
                    raise  # Re-raise after all retries exhausted
            else:
                # If it's a different error, raise immediately
                logger.error(f"an error occurred during llm.stream_chat call",
                             exception=e)
                raise
