from enum import Enum
from pydantic import BaseModel
from llama_index.core.base.llms.types import ChatMessage, MessageRole
from cpaibot.llm.base import llm_chat
from cpaibot.common.utils import extract_json_response
from cpaibot.common.model.chat import Message, MessagePartType
from cpaibot.agents.internal.misc import preferences
from cpaibot.managers.chat import create_chat_message_part, update_message_part


class IntentType(str, Enum):
    QUESTION = "QUESTION"
    LAUNCH = "LAUNCH"
    MODIFY_PAYLOAD = "MODIFY_PAYLOAD"
    MIXED = "MIXED"
    CHAT = "CHAT"


class Intent(BaseModel):
    intent: IntentType
    reasoning: str
    entities: list[str]


def classify_intent(
        response_message: Message,
        messages: list[Message],
) -> Intent:
    """
    Classify user intent into one of:
    - QUESTION: User asking about platform features/documentation
    - LAUNCH: User wants to launch a job
    - MODIFY_PAYLOAD: User wants to modify previous launch payload
    - MIXED: Both question and launch request
    - CHAT: General conversation
    """

    if len(messages) == 0:
        raise RuntimeError("No messages received")

    user_message = next((m for m in messages[::-1] if m.role == MessageRole.USER), None)
    user_message_text = user_message.to_llama_index().content if user_message is not None else ""

    classification_prompt = f"""You are an intent classifier for an {preferences.deployment_name} cloud platform chatbot.
    
    User message: "{user_message_text}"
    
    Classify the intent into ONE of the following categories:
    
    1. QUESTION - User is asking how to do something, asking about features, troubleshooting
       Examples: "How do I launch pipeline?", "Can I use spot instances?", "What is auto-scaling?"
    
    2. LAUNCH - User wants to launch a job/instance
       Examples: "Launch ubuntu with 32GB RAM", "Start a xxxxx pipeline", "Run blast on 4 nodes"
    
    3. MODIFY_PAYLOAD - User wants to modify a previously suggested launch configuration
       Examples: "Change RAM to 64GB", "Add another worker node", "Use spot instances instead"
    
    4. MIXED - User is asking a question AND wants to launch something
       Examples: "How do I configure auto-scaling? Please set it up for me with 32GB RAM"
    
    5. CHAT - General conversation, greetings, thanks
       Examples: "Hello", "Thanks!", "That's helpful"
    
    Respond with ONLY the category name and a brief reasoning.
    
    Format: JSON string of {{"intent", "reasoning", "entities"}} format:
    `{{"intent": "<category>", "reasoning": "<one sentence>", "entities": <key entities extracted from the message as array of strings>]}}`
    
    ### Example Outputs
    
    **Input**: "How can I launch a XXXX pipeline?"
    `{{"intent": "QUESTION", "reasoning": "User is asking for information about launching XXXX pipeline", "entities": ["XXXX", "pipeline", "launch"]}}`
    
    **Input**: "Launch ubuntu with 32GB RAM and 500GB disk"
    `{{"intent": "LAUNCH", "reasoning": "User explicitly requests launching an instance with specific specs", "entities": ["ubuntu", "32GB RAM", "500GB disk"]}}`
    
    **Input**: "Can I use spot instances? If yes, launch one with 16GB RAM"
    `{{"intent": "MIXED", "reasoning": "User asks about capability and wants to launch", "entities": ["spot instances", "16GB RAM"]}}`
    """

    part = create_chat_message_part(response_message, part_type=MessagePartType.CONTEXT)

    response = llm_chat(
        [*messages, classification_prompt],
        part=part,
    )

    parsed = extract_json_response(response)
    try:
        return Intent(**parsed)
    except:
        raise RuntimeError("Unable to classify user query")
