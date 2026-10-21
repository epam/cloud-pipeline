from cpaibot.managers.chat import (load_conversation,
                                   create_chat_message_part,
                                   update_message,
                                   update_chat,
                                   update_conversation,
                                   update_message_part)
from llama_index.core.base.llms.types import MessageRole
from cpaibot.common.model.chat import Message, MessagePartType
from cpaibot.managers.chat import load_message_details
from cpaibot.agents.internal import (classify_intent,
                                     generate_plan,
                                     ActionType,
                                     get_documentation_info,
                                     get_generic_response,
                                     get_launch_result,
                                     generate_chat_title)
from cpaibot.common.logger import CpLogger as Logger

default_logger = Logger("cpaibot")


def process_assistant_message(
        response_message: Message | str,
        logger: Logger | None = None,
):
    if logger is None:
        logger = default_logger
    message_id = response_message.identifier if isinstance(response_message, Message) else response_message
    message_id = str(message_id)[:8]
    try:
        logger.info(f"processing message #{message_id}...")
        logger.info(f"loading message #{message_id} details")
        response_message = load_message_details(response_message, force=True)
        response_message.pending = True
        update_message(response_message)
        logger.info(f"message #{message_id} details loaded")
        if not response_message.chat:
            raise RuntimeError("Chat not found")
        conversations = response_message.branches or []
        if len(conversations) == 0:
            raise RuntimeError("No conversations found")
        if response_message.role != MessageRole.ASSISTANT:
            raise RuntimeError("Only ASSISTANT messages processing allowed")
        active_conversation = conversations[0]
        logger.info(f"message #{message_id} active conversation: {active_conversation.identifier}")
        active_conversation.pending = True
        update_conversation(active_conversation)
        logger.info(f"message #{message_id}: loading conversation messages...")
        messages = load_conversation(conversation=active_conversation.identifier)
        logger.info(f"message #{message_id}: {len(messages)} conversation messages loaded")
        messages = [m for m in messages if not m.message_is_empty()]
        logger.info(f"message #{message_id}: {len(messages)} non-empty messages")
        try:
            if not response_message.chat.title:
                response_message.status = "Generating chat title..."
                update_message(response_message)
                logger.info(f"message #{message_id}: generating chat title...")
                response_message.chat.title = generate_chat_title(messages)
                update_chat(response_message.chat)
                response_message.status = None
                update_message(response_message)
                logger.info(f"message #{message_id}: chat title generated: {response_message.chat.title}")

            logger.info(f"message #{message_id}: classifying intent...")
            response_message.status = "Analyzing..."
            update_message(response_message)
            intent = classify_intent(response_message, messages)
            logger.info(f"message #{message_id}: intent classified: {intent.model_dump()}")
            logger.info(f"message #{message_id}: generating execution plan...")
            planning = generate_plan(response_message, messages, intent)

            response_message.status = None
            update_message(response_message)

            if not planning:
                logger.info(f"message #{message_id}: no execution plan generated, using default LLM to answer...")
                get_generic_response(messages, response_message)
                return

            logger.info(f"message #{message_id}: execution plan generated ({len(planning)} steps)")
            for action_idx, action in enumerate(planning):
                logger.info(f"message #{message_id}: step {action_idx + 1}) [{action.type}] \"{action.action}\"")

            for action_idx, action in enumerate(planning):
                logger.info(f"message #{message_id}: performing step "
                            f"{action_idx + 1}) [{action.type}] \"{action.action}\"...")
                if action.type == ActionType.DOCUMENTATION:
                    continue_execution = get_documentation_info(messages, response_message, action=action)
                elif action.type == ActionType.LAUNCH:
                    continue_execution = get_launch_result(messages, response_message, action=action)
                else:
                    continue_execution = get_generic_response(messages, response_message, action=action)
                if not continue_execution:
                    logger.info(f"message #{message_id}: stopping execution")
                    break
        except Exception as e:
            logger.error(f"error processing message #{message_id}", exception=e)
            error_part = create_chat_message_part(response_message, part_type=MessagePartType.ERROR)
            error_part.errors.append(e.__str__())
            update_message_part(error_part)
        finally:
            logger.info(f"finalizing message #{message_id} processing")
            response_message.status = None
            response_message.pending = False
            update_message(response_message)
            active_conversation.pending = False
            update_conversation(active_conversation)
            logger.info(f"message #{message_id} processed")
            logger.info(response_message.to_string(filter_out_context_parts=True))
    except Exception as e:
        logger.info(f"error processing message #{message_id}",
                    exception=e)
