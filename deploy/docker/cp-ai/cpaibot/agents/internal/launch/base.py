import json
from pydantic import BaseModel, Field
from enum import Enum
from cpaibot.agents.pipeline.types import LaunchPayload


class LaunchMode(str, Enum):
    TOOL = "tool"
    PIPELINE = "pipeline"


class LaunchPayloadIntent(BaseModel):
    mode: LaunchMode = Field(
        description="Whether launching a tool (image like ubuntu/centos) or a pipeline (workflow)"
    )
    is_update: bool = Field(
        description="True if modifying existing configuration, False if creating new"
    )
    details: str = Field(
        description="Concise technical specifications: names, IDs, paths, parameters, resources"
    )
    previous_launch_payload: LaunchPayload | None = None

    def to_markdown(self) -> str:
        intent_update = ""
        if self.is_update:
            intent_update = f", updating existing payload"
        prev_payload = ""
        if self.previous_launch_payload:
            prev = json.dumps(self.previous_launch_payload.to_json(), indent=2)
            prev = f"```json\n{prev}\n```"
            if self.is_update:
                prev_payload = f"\nPrevious launch payload to update:\n{prev}\n\n"
            else:
                prev_payload = f"\nPrevious launch payload:\n{prev}\n\n"
        return f"""Launch mode: "{self.mode}"{intent_update}\nLaunch details: "{self.details}"{prev_payload}"""