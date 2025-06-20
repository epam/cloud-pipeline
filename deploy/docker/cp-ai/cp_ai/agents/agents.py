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
from cp_ai.database import search_platform_documents_and_issues
from cp_ai.llm import llm


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
            build_tool(search_platform_documents_and_issues, **kwargs)
        ],
        verbose=True,
        llm=llm,
        memory=BaseMemory.from_defaults()
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
    user_query = last_message.content
    prompt = f'''
        This is user query, provide an answer for this query using provided agents or general LLM knowledge:
        -------------
        {user_query}
        -------------
        Instructions: 
        - Do not use tools if it is not requested directly.
        - If the tool output contains responses of format "<<<...>>>" (e.g. "<<<LAUNCH:...>>>"), always include such responses AS IS to the final response;
        such blocks (<<<...>>>) are essential and contains technical details about user query.
        - If the tool output contains **references**, include such references to the final response.
        - If 'LaunchException' is returned from some of the agent, STOP execution and request details from user considering LaunchException info.
        - Use Markdown format; include all available links and references (prefer format [entity name](entity url)].
        '''
    default_agent = get_default_agent(**kwargs)
    return await default_agent.astream_chat(
        message=prompt,
        chat_history=_messages
    )
