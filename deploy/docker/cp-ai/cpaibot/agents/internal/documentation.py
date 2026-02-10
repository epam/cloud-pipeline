from llama_index.core.base.llms.types import MessageRole, ChatMessage
from cpaibot.common.model.chat import Message
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.agents.internal.misc import default_logger
from cpaibot.llm.base import llm_chat
from cpaibot.managers.chat import create_chat_message_part, update_message_part
from cpaibot.agents.internal.planning import Action
from cpaibot.agents.github.agent import query_documents


def get_documentation_info(
        messages: list[Message],
        response_message: Message,
        action: Action | None = None,
        filter_out_context_parts = False,
        logger: Logger | None = None,
) -> bool:
    if not logger:
        logger = default_logger
    part = create_chat_message_part(response_message)
    message_id = response_message.identifier
    message_id = message_id[:8]
    def log_status(status: str | None):
        if part:
            part.status = status
            update_message_part(part)
        if status:
            logger.info(f"messsage #{message_id}: {status}")
    try:
        messages = [m.to_llama_index(filter_out_context_parts) for m in messages]
        chat_messages = [*messages, response_message.to_llama_index(filter_out_context_parts)]
        if action:
            chat_messages.append(ChatMessage(
                content=action.action,
                role=MessageRole.USER,
            ))
        chat_messages.append(ChatMessage(
            content="Generate a focused search query for the vector database. Keep it concise and specific.\n\n"
                    "Examples:\n"
                    "User: 'How do I configure Docker for Nextflow?'\n"
                    "Query: 'Nextflow Docker configuration setup'\n\n"
                    "User: 'Check documentation for error handling in processes'\n"
                    "Query: 'Nextflow process error handling and retry strategies'\n\n"
                    "Now generate a similar focused query:\n"
                    "Query:",
            role=MessageRole.USER,
        ))
        log_status("Thinking...")
        query = llm_chat(chat_messages, part=part, logger=logger)
        log_status(f"Querying documentation: \"{query}\"")
        query += (
            "\n\n____________\n"
            "Using the documentation provided, create a comprehensive and detailed answer that:\n\n"
            "1. **Direct Answer**: Start with a clear, direct response to the question\n"
            "2. **Step-by-Step Instructions**: Provide concrete steps, commands, or configuration examples\n"
            "3. **Code Examples**: Include relevant code snippets, configuration files, or command-line examples when applicable\n"
            "4. **Important Details**: Explain key parameters, options, or settings\n"
            "5. **Context**: Briefly explain why certain approaches are recommended\n"
            "6. **Related Information**: Mention related features or alternative approaches if relevant\n\n"
            "Requirements:\n"
            "- Write in clear, tutorial-style prose referring to 'the documentation' when citing sources\n"
            "- Minimum 3-4 paragraphs for complex topics\n"
            "- Include specific examples with actual syntax/commands\n"
            "- Include images (in markdown format) where applicable\n"
            "- Avoid generic statements; be specific and actionable\n\n"
            "Detailed answer:"
        )
        query_documents(query, message=part)
        return True
    except Exception as e:
        part.errors.append(e.__str__())
        logger.error(f"message #{message_id}: failed to query documentation",
                     exception=e)
        return False
    finally:
        log_status(None)
