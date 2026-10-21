import time
import asyncio
from typing import Generator, Callable, Any, Awaitable
from cpaibot.common.model.chat import (Chat,
                                       ChatBranch,
                                       Message,
                                       MessagePart,
                                       MessagePartType)
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.database.chat import ChatsManager, ChatsProtocol
from llama_index.core.base.llms.types import MessageRole, ChatResponseGen


default_logger = Logger("cpaibot")


def create_chat(
        *,
        chat: Chat | None = None,
        user: str | None = None,
) -> Chat:
    with ChatsManager() as manager:
        if chat is None:
            chat = manager.upsert_chat(Chat.create(user=user))
        elif isinstance(chat, Chat):
            chat = chat
        else:
            raise RuntimeError(f"Unexpected chat type {type(chat).__str__}")
        if chat.active_branch_id:
            active_branch = manager.get_branch(chat.active_branch_id)
            if not active_branch:
                active_branch = manager.upsert_branch(ChatBranch.create(chat.identifier))
        else:
            active_branch = manager.upsert_branch(ChatBranch.create(chat.identifier))

        if not active_branch:
            raise RuntimeError(f"Unable to create new conversation for chat {chat.identifier}")

        chat.active_branch_id = active_branch.identifier
        chat.mark_updated()
        manager.upsert_chat(chat)

        return chat


async def create_chat_async(
        *,
        chat: Chat | None = None,
        user: str | None = None,
) -> Chat:
    async with ChatsManager() as manager:
        if chat is None:
            chat = await manager.upsert_chat_async(Chat.create(user=user))
        elif isinstance(chat, Chat):
            chat = chat
        else:
            raise RuntimeError(f"Unexpected chat type {type(chat).__str__}")
        if chat.active_branch_id:
            active_branch = await manager.get_branch_async(chat.active_branch_id)
            if not active_branch:
                active_branch = await manager.upsert_branch_async(ChatBranch.create(chat.identifier))
        else:
            active_branch = await manager.upsert_branch_async(ChatBranch.create(chat.identifier))

        if not active_branch:
            raise RuntimeError(f"Unable to create new conversation for chat {chat.identifier}")

        chat.active_branch_id = active_branch.identifier
        chat.mark_updated()
        await manager.upsert_chat_async(chat)

        return chat


def create_chat_message(
        *,
        text: str | None = None,
        chat: Chat | str | None = None,
        conversation: ChatBranch | str | None = None,
        edit_message_id: str | None = None,
        logger: Logger | None = None,
        user: str | None = None,
        role: MessageRole | None = None,
) -> Message:
    with ChatsManager() as manager:
        if chat is None:
            chat = manager.upsert_chat(Chat.create(user=user))
        elif isinstance(chat, Chat):
            chat = chat
        elif isinstance(chat, str):
            chat_instance = manager.get_chat(chat)
            if not chat_instance:
                raise RuntimeError(f"Chat {chat} not found")
            chat = chat_instance
        else:
            raise RuntimeError(f"Unexpected chat identifier {type(chat).__str__}")

        active_branch = conversation
        if active_branch and isinstance(active_branch, str):
            active_branch_instance = manager.get_branch(active_branch)
            if not active_branch_instance:
                raise RuntimeError(f"Conversation {active_branch} not found")
            active_branch = active_branch_instance
        elif not active_branch and chat.active_branch_id:
            active_branch = manager.get_branch(chat.active_branch_id)

        if not active_branch:
            active_branch = manager.upsert_branch(ChatBranch.create(chat.identifier))
            if not active_branch:
                # something went wrong
                raise RuntimeError(f"Unable to load chat conversation (current branch not found)")
            chat.active_branch_id = active_branch.identifier
            chat.mark_updated()
            manager.upsert_chat(chat)

        if edit_message_id:
            edit_message = next((o for o in active_branch.messages if o.lower() == edit_message_id.lower()), None)
            if edit_message:
                messages = active_branch.messages[:active_branch.messages.index(edit_message)]
            else:
                messages = []

            #creating new branch
            active_branch = manager.upsert_branch(ChatBranch.create(chat.identifier, messages=messages))
            if not active_branch:
                # something went wrong
                raise RuntimeError(f"Unable to create new chat conversation branch")

        if len(active_branch.messages) > 0:
            prev_message_id = active_branch.messages[-1]
        else:
            prev_message_id = None

        message = Message.create(chat.identifier, role=role, previous_id=prev_message_id)
        active_branch.messages.append(message.identifier)
        active_branch.mark_updated()
        message = manager.upsert_message(message)
        manager.upsert_branch(active_branch)

        message = load_message_details(message)

    if text:
        create_chat_message_part(message, text=text)

    return message


async def create_chat_message_async(
        *,
        text: str | None = None,
        chat: Chat | str | None = None,
        conversation: ChatBranch | str | None = None,
        edit_message_id: str | None = None,
        logger: Logger | None = None,
        user: str | None = None,
        role: MessageRole | None = None,
) -> Message:
    async with ChatsManager() as manager:
        if chat is None:
            chat = await manager.upsert_chat_async(Chat.create(user=user))
        elif isinstance(chat, Chat):
            chat = chat
        elif isinstance(chat, str):
            chat_instance = await manager.get_chat_async(chat)
            if not chat_instance:
                raise RuntimeError(f"Chat {chat} not found")
            chat = chat_instance
        else:
            raise RuntimeError(f"Unexpected chat identifier {type(chat).__str__}")

        active_branch = conversation
        if active_branch and isinstance(active_branch, str):
            active_branch_instance = await manager.get_branch_async(active_branch)
            if not active_branch_instance:
                raise RuntimeError(f"Conversation {active_branch} not found")
            active_branch = active_branch_instance
        elif not active_branch and chat.active_branch_id:
            active_branch = await manager.get_branch_async(chat.active_branch_id)

        if not active_branch:
            active_branch = await manager.upsert_branch_async(ChatBranch.create(chat.identifier))
            if not active_branch:
                # something went wrong
                raise RuntimeError(f"Unable to load chat conversation (current branch not found)")
            chat.active_branch_id = active_branch.identifier
            chat.mark_updated()
            await manager.upsert_chat_async(chat)

        if edit_message_id:
            edit_message = next((o for o in active_branch.messages if o.lower() == edit_message_id.lower()), None)
            if edit_message:
                messages = active_branch.messages[:active_branch.messages.index(edit_message)]
            else:
                messages = []

            #creating new branch
            active_branch = await manager.upsert_branch_async(ChatBranch.create(chat.identifier, messages=messages))
            if not active_branch:
                # something went wrong
                raise RuntimeError(f"Unable to create new chat conversation branch")

        if len(active_branch.messages) > 0:
            prev_message_id = active_branch.messages[-1]
        else:
            prev_message_id = None

        message = Message.create(chat.identifier, role=role, previous_id=prev_message_id)
        active_branch.messages.append(message.identifier)
        active_branch.mark_updated()
        message = await manager.upsert_message_async(message)
        await manager.upsert_branch_async(active_branch)

        message = await load_message_details_async(message)

    if text:
        create_chat_message_part(message, text=text)

    return message

def update_chat(
        chat: Chat,
        /,
        chats_protocol: ChatsProtocol | None = None,
):
    def update(manager: ChatsProtocol):
        nonlocal chat
        chat.mark_updated()
        manager.upsert_chat(chat)

    if chats_protocol:
        update(chats_protocol)
    else:
        with ChatsManager() as chats_protocol:
            update(chats_protocol)


async def update_chat_async(
        chat: Chat,
        /,
        chats_protocol: ChatsProtocol | None = None,
):
    async def update(manager: ChatsProtocol):
        nonlocal chat
        chat.mark_updated()
        manager.upsert_chat(chat)

    if chats_protocol:
        await update(chats_protocol)
    else:
        async with ChatsManager() as chats_protocol:
            await update(chats_protocol)


def update_message(
        message: Message,
        /,
        chats_protocol: ChatsProtocol | None = None,
):
    def update(manager: ChatsProtocol):
        nonlocal message
        message = load_message_details(message, force=True)
        message.mark_updated()
        manager.upsert_message(message)
        if message.chat:
            message.chat.mark_updated()
            manager.upsert_chat(message.chat)
        if message.branches:
            for branch in message.branches:
                branch.mark_updated()
                manager.upsert_branch(branch)

    if chats_protocol:
        update(chats_protocol)
    else:
        with ChatsManager() as chats_protocol:
            update(chats_protocol)


async def update_message_async(
        message: Message,
        /,
        chats_protocol: ChatsProtocol | None = None,
):
    async def update(manager: ChatsProtocol):
        nonlocal message
        message = await load_message_details_async(message, force=True)
        message.mark_updated()
        await manager.upsert_message_async(message)
        if message.chat:
            message.chat.mark_updated()
            await manager.upsert_chat_async(message.chat)
        if message.branches:
            for branch in message.branches:
                branch.mark_updated()
                await manager.upsert_branch_async(branch)

    if chats_protocol:
        await update(chats_protocol)
    else:
        async with ChatsManager() as chats_protocol:
            await update(chats_protocol)



def update_conversation(
        conversation: ChatBranch,
        /,
        chats_protocol: ChatsProtocol | None = None,
):
    def update(manager: ChatsProtocol):
        nonlocal conversation
        conversation.mark_updated()
        manager.upsert_branch(conversation)
        chat = manager.get_chat(conversation.chat_id)
        if chat:
            chat.mark_updated()
            manager.upsert_chat(chat)
    if chats_protocol:
        update(chats_protocol)
    else:
        with ChatsManager() as chats_protocol:
            update(chats_protocol)


def create_chat_message_part(
        message: Message,
        /,
        text: str | None = None,
        part_type: MessagePartType | None = None,
) -> MessagePart:
    message = load_message_details(message)
    with ChatsManager() as manager:
        message_part = MessagePart.create(message.chat_id, message.identifier)
        message.parts.append(message_part)
        if text:
            message_part.text = text
            message_part.type = MessagePartType.TEXT
        if part_type:
            message_part.type = part_type

        message_part = manager.upsert_message_part(message_part)
        update_message(message, chats_protocol=manager)
        return message_part


async def create_chat_message_part_async(
        message: Message,
        /,
        text: str | None = None,
        part_type: MessagePartType | None = None,
) -> MessagePart:
    message = await load_message_details_async(message)
    async with ChatsManager() as manager:
        message_part = MessagePart.create(message.chat_id, message.identifier)
        message.parts.append(message_part)
        if text:
            message_part.text = text
            message_part.type = MessagePartType.TEXT
        if part_type:
            message_part.type = part_type

        message_part = await manager.upsert_message_part_async(message_part)
        await update_message_async(message, chats_protocol=manager)
        return message_part


def update_message_part(
        part: MessagePart,
) -> MessagePart:
    with ChatsManager() as manager:
        part.mark_updated()
        part = manager.upsert_message_part(part)
        message = load_message_details(part.message_id)
        update_message(message, chats_protocol=manager)
        return part


async def update_message_part_async(
        part: MessagePart,
) -> MessagePart:
    async with ChatsManager() as manager:
        part.mark_updated()
        part = await manager.upsert_message_part_async(part)
        message = await load_message_details_async(part.message_id)
        await update_message_async(message, chats_protocol=manager)
        return part


def update_message_part_from_gen(
        part: MessagePart,
        gen: ChatResponseGen | Generator[str, None, None],
) -> MessagePart:
    prev = time.time()
    delay = 0.5
    with ChatsManager() as manager:
        message = load_message_details(part.message_id)
        if not part.text:
            part.text = ""
        for r in gen:
            if isinstance(r, str):
                part.text += r
            else:
                part.text += r.delta
            part.mark_updated()
            cur = time.time()
            if cur - prev >= delay:
                part = manager.upsert_message_part(part)
                update_message(message, chats_protocol=manager)
            prev = cur
        part = manager.upsert_message_part(part)
        update_message(message, chats_protocol=manager)
        return part

def load_conversation(
        *,
        chat: Chat | str | None = None,
        conversation: str | ChatBranch | None = None,
) -> list[Message]:
    with ChatsManager() as chats_manager:
        if not chat and not conversation:
            raise RuntimeError(f"Either chat or conversation must be specified")
        elif chat:
            chat_id = chat.identifier if isinstance(chat, Chat) else chat
            chat = chats_manager.get_chat(chat_id)
            if not chat:
                raise RuntimeError(f"Chat {chat_id} not found")
            if not chat.active_branch:
                raise RuntimeError(f"Chat {chat_id} conversation not found")
            conversation = chats_manager.get_branch(chat.active_branch_id)
            if not conversation:
                raise RuntimeError(f"Chat {chat_id} conversation {chat.active_branch_id} not found")
        else:
            conversation_id = conversation.identifier if isinstance(conversation, ChatBranch) else conversation
            conversation = chats_manager.get_branch(conversation_id)
            if conversation is None:
                raise RuntimeError(f"Conversation {conversation_id} not found")
            chat = chats_manager.get_chat(conversation.chat_id)
            if chat is None:
                raise RuntimeError(f"Chat {conversation.chat_id} not found for conversation {conversation_id}")
        messages = chats_manager.list_messages(
            chat.identifier,
            messages_ids=conversation.messages,
        )
        all_parts = chats_manager.list_message_parts(conversation.messages)
        for message in messages:
            message.chat = chat
            message.branches = [conversation]
            message.parts = [p for p in all_parts if p.message_id == message.identifier]
        return messages


async def load_conversation_async(
        *,
        chat: Chat | str | None = None,
        conversation: str | ChatBranch | None = None,
) -> list[Message]:
    async with ChatsManager() as chats_manager:
        if not chat and not conversation:
            raise RuntimeError(f"Either chat or conversation must be specified")
        elif chat:
            chat_id = chat.identifier if isinstance(chat, Chat) else chat
            chat = await chats_manager.get_chat_async(chat_id)
            if not chat:
                raise RuntimeError(f"Chat {chat_id} not found")
            if not chat.active_branch_id:
                raise RuntimeError(f"Chat {chat_id} conversation not found")
            conversation = await chats_manager.get_branch_async(chat.active_branch_id)
            if not conversation:
                raise RuntimeError(f"Chat {chat_id} conversation {chat.active_branch_id} not found")
        else:
            conversation_id = conversation.identifier if isinstance(conversation, ChatBranch) else conversation
            conversation = await chats_manager.get_branch_async(conversation_id)
            if conversation is None:
                raise RuntimeError(f"Conversation {conversation_id} not found")
            chat = chats_manager.get_chat(conversation.chat_id)
            if chat is None:
                raise RuntimeError(f"Chat {conversation.chat_id} not found for conversation {conversation_id}")
        messages = await chats_manager.list_messages_async(
            chat.identifier,
            messages_ids=conversation.messages,
        )
        all_parts = await chats_manager.list_message_parts_async(conversation.messages)
        for message in messages:
            message.chat = chat
            message.branches = [conversation]
            message.parts = [p for p in all_parts if p.message_id == message.identifier]
        return messages


def load_message_details(
        message: Message | str,
        /,
        chats_manager: ChatsProtocol | None = None,
        force = False,
) -> Message:
    def load(prot: ChatsProtocol):
        nonlocal message
        if isinstance(message, str):
            message_instance = prot.get_message(message)
            if not message_instance:
                raise RuntimeError(f"Message {message} not found")
            message = message_instance
        if message.chat is None or force:
            message.chat = prot.get_chat(message.chat_id)
            if message.chat is None:
                raise RuntimeError(f"Chat {message.chat_id} not found")
        if not message.branches or force:
            branches = prot.list_branches(message.chat_id)
            message.branches = [b for b in branches if message.identifier in b.messages]
        if not message.parts or force:
            all_parts = prot.list_message_parts(message.identifier)
            message.parts = [p for p in all_parts if p.message_id == message.identifier]
        return message
    if chats_manager is None:
        with ChatsManager() as chats_manager:
            return load(chats_manager)
    else:
        return load(chats_manager)


async def load_message_details_async(
        message: Message | str,
        /,
        chats_manager: ChatsProtocol | None = None,
        force: bool = False,
) -> Message:
    async def load(prot: ChatsProtocol):
        nonlocal message
        if isinstance(message, str):
            message_instance = await prot.get_message_async(message)
            if not message_instance:
                raise RuntimeError(f"Message {message} not found")
            message = message_instance
        if message.chat is None or force:
            message.chat = await prot.get_chat_async(message.chat_id)
            if message.chat is None:
                raise RuntimeError(f"Chat {message.chat_id} not found")
        if not message.branches or force:
            branches = await prot.list_branches_async(message.chat_id)
            message.branches = [b for b in branches if message.identifier in b.messages]
        if not message.parts or force:
            all_parts = await prot.list_message_parts_async(message.identifier)
            message.parts = [p for p in all_parts if p.message_id == message.identifier]
        return message
    if chats_manager is None:
        async with ChatsManager() as chats_manager:
            return await load(chats_manager)
    else:
        return await load(chats_manager)


def generate_empty_response_message(
        user_message: Message | str,
        logger: Logger | None = None,
) -> Message:
    """"""
    if logger is None:
        logger = default_logger
    message_id = user_message.identifier if isinstance(user_message, Message) else user_message
    message_id = str(message_id)[:8]
    try:
        logger.info(f"submitting message #{message_id}...")
        logger.info(f"loading message #{message_id} details")
        user_message = load_message_details(user_message)
        logger.info(f"message #{message_id} details loaded")
        if not user_message.chat:
            raise RuntimeError("Chat not found")
        conversations = user_message.branches or []
        if len(conversations) == 0:
            raise RuntimeError("No conversations found")
        if user_message.role != MessageRole.USER:
            raise RuntimeError("Only USER messages submitting allowed")
        active_conversation = conversations[0]
        response_message = create_chat_message(
            chat=user_message.chat,
            conversation=active_conversation,
            logger=logger,
            role=MessageRole.ASSISTANT,
        )
        response_message.pending = True
        update_message(response_message)
        logger.info(f"message #{message_id}: submitted. Response message: {response_message.identifier}")
        return response_message
    except Exception as e:
        logger.info(f"error submitting message #{message_id}",
                    exception=e)
        raise e


async def generate_empty_response_message_async(
        user_message: Message | str,
        logger: Logger | None = None,
) -> Message | None:
    """"""
    if logger is None:
        logger = default_logger
    message_id = user_message.identifier if isinstance(user_message, Message) else user_message
    message_id = str(message_id)[:8]
    try:
        logger.info(f"submitting message #{message_id}...")
        logger.info(f"loading message #{message_id} details")
        user_message = await load_message_details_async(user_message)
        logger.info(f"message #{message_id} details loaded")
        if not user_message.chat:
            raise RuntimeError("Chat not found")
        conversations = user_message.branches or []
        if len(conversations) == 0:
            raise RuntimeError("No conversations found")
        if user_message.role != MessageRole.USER:
            raise RuntimeError("Only USER messages submitting allowed")

        contentful_parts = [p for p in user_message.parts if p.is_contentful and not p.is_empty]
        if len(contentful_parts) == 0:
            logger.info(f"message #{message_id}: no contentful parts. Assistant response will not be generated")
            return None

        active_conversation = conversations[0]
        active_conversation.pending = True
        update_conversation(active_conversation)
        response_message = await create_chat_message_async(
            chat=user_message.chat,
            conversation=active_conversation,
            logger=logger,
            role=MessageRole.ASSISTANT,
        )
        response_message.pending = True
        update_message(response_message)
        logger.info(f"message #{message_id}: submitted. Response message: {response_message.identifier}")
        return response_message
    except Exception as e:
        logger.info(f"error submitting message #{message_id}",
                    exception=e)
        raise e


def subscribe_to_chat_message_update(
        message_id: str,
        callback: Callable[[str, Any], None] | None = None,
        /,
        interval_sec = 0.1
):
    if interval_sec is None:
        interval_sec = 0.1
    with ChatsManager() as manager:
        pending = True
        tmstmp = None
        parts = {}
        while pending:
            message = manager.get_message(message_id)
            if not message:
                raise RuntimeError(f"Message {message_id[:8]} not found")
            message = load_message_details(message)
            pending = message.pending
            updated = tmstmp is None or tmstmp < message.timestamp
            if updated and callback is not None:
                callback("update_message", message.to_json())
            for part in message.parts:
                part_tmpstmp = part.timestamp
                current = parts.get(part.identifier, None)
                part_updated = current is None or current < part_tmpstmp
                if part_updated and callback is not None:
                    callback("update_part", part.to_json())
                parts.update({part.identifier: part_tmpstmp})
            tmstmp = message.timestamp
            time.sleep(interval_sec)


async def subscribe_to_chat_message_update_async(
        message_id: str,
        callback: Callable[[str, Any], Awaitable[None]] | None = None,
        /,
        interval_sec = 0.1
):
    if interval_sec is None:
        interval_sec = 0.1
    async with ChatsManager() as manager:
        pending = True
        tmstmp = None
        parts: dict[str, Any] = {}

        while pending:
            message = await manager.get_message_async(message_id)
            if not message:
                raise RuntimeError(f"Message {message_id[:8]} not found")

            message = await load_message_details_async(message)
            pending = message.pending
            updated = tmstmp is None or tmstmp < message.timestamp

            if updated and callback is not None:
                await callback("update_message", message.to_json())

            for part in message.parts:
                part_tmpstmp = part.timestamp
                current = parts.get(part.identifier)
                part_updated = current is None or current < part_tmpstmp

                if part_updated and callback is not None:
                    await callback("update_part", part.to_json())

                parts[part.identifier] = part_tmpstmp

            tmstmp = message.timestamp
            await asyncio.sleep(interval_sec)
