from typing import Callable, Any, Union
from llama_index.core.memory import BaseMemory
from llama_index.core.tools import FunctionTool
from llama_index.core.agent import ReActAgent
from llama_index.core.types import ChatMessage
from llama_index.core.chat_engine.types import StreamingAgentChatResponse
from .tools import (get_command_to_run_compute_instance,
                    get_command_to_run_pipeline,
                    stop_compute_instance,
                    get_compute_instance_state)
from cp_ai.database import search_platform_documentation
from cp_ai.llm import llm, llm_simple_query


def build_tool(
        fn: Callable[..., Any],
        /,
        name: str | None = None,
        description: str | None = None,
        **kwargs
):
    return FunctionTool.from_defaults(fn, name=name, description=description, partial_params=kwargs)

def get_default_agent(**kwargs):
    return ReActAgent(
        tools=[
            build_tool(get_command_to_run_compute_instance, **kwargs),
            build_tool(get_command_to_run_pipeline, **kwargs),
            build_tool(stop_compute_instance, **kwargs),
            build_tool(get_compute_instance_state, **kwargs),
            build_tool(search_platform_documentation, **kwargs)
        ],
        verbose=True,
        llm=llm,
        memory=BaseMemory.from_defaults()
    )


def retrieve_platform_information(query: str, context: str | None = None, **kwargs):
    """Useful for answering user questions about how the platform operates,
    how perform different actions on the platform, and other questions that can be answered
    using the platform's documentation"""
    all_context = ''
    if context:
        all_context += context + '\n\n'
    all_context += query
    return search_platform_documentation(
        query=query,
        user_query=all_context,
        **kwargs
    )


async def answer_using_default_agent(
        message: str | ChatMessage | None = None,
        messages: list[Union[ChatMessage, str]] | None = None,
        **kwargs
) -> StreamingAgentChatResponse:
    def map_message(m: Union[ChatMessage, str]):
        if isinstance(m, str):
            return ChatMessage(content=m)
        return m
    if messages is None:
        messages = []
    _messages = [map_message(m) for m in messages]
    if message is not None:
        _messages.append(map_message(message))

    last_message = _messages.pop()
    history = '\n\n'.join([m.__str__() for m in _messages])
    user_query = last_message.content
    prompt = f'''
        This is user query, provide an answer for this query using provided agents or general LLM knowledge:
        -------------
        {history}
        {user_query}
        -------------
        IMPORTANT: 
        - You must include any and all blocks that look like `<<<...>>>` verbatim and exactly as they appeared in the tool output.
        - Do not rephrase, omit, or filter out these `<<<...>>>` blocks. Treat them as immutable text.
        - If the tool output contains **references**, include such references to the final response.
        - If 'LaunchException' is returned from some of the agent, STOP execution and request details from user considering LaunchException info.
        - Use Markdown format; include all available links and references (prefer format [entity name](entity url)].
        '''
    default_agent = get_default_agent(**kwargs)
    return await default_agent.astream_chat(
        message=prompt,
    )
