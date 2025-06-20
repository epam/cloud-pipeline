import re
import json
import base64
from uuid import uuid4
from typing import Any
from .settings import cp_ai_settings


def get_application_entity_url(entity_uri: str) -> str | None:
    app_url = cp_ai_settings.CP_APPLICATION_URI
    if app_url is None:
        return None
    if not app_url.endswith('/'):
        app_url += '/'
    while entity_uri.startswith('/'):
        entity_uri = entity_uri[1:]
    return f'{app_url}{entity_uri}'


def extract_json_response(response: str) -> Any:
    if not response:
        return None
    resp = response
    resp = re.sub(r'^\s*```(json)?|```\s*$', '', resp, flags=re.DOTALL).strip()
    resp = re.sub(r'^\s*`|`\s*$', '', resp, flags=re.DOTALL).strip()
    try:
        return json.loads(resp)
    except:
        return None


def remove_quotes(response: str) -> str:
    if not response:
        return ''
    resp = response
    resp = re.sub(r"^\s*'|'\s*$", '', resp, flags=re.DOTALL).strip()
    resp = re.sub(r'^\s*"|"\s*$', '', resp, flags=re.DOTALL).strip()
    return resp


def _base64url_decode(input_str: str):
    # Add padding if needed
    rem = len(input_str) % 4
    if rem > 0:
        input_str += '=' * (4 - rem)
    return base64.urlsafe_b64decode(input_str)


def simple_decode_jwt(token: str) -> dict:
    try:
        header_b64, payload_b64, signature_b64 = token.split(".")

        header = json.loads(_base64url_decode(header_b64))
        payload = json.loads(_base64url_decode(payload_b64))

        return {
            "header": header,
            "payload": payload,
            "signature": signature_b64  # raw base64 string
        }
    except:
        return {}


def get_username_from_bearer(bearer: str | None) -> str | None:
    if bearer is None:
        return None
    try:
        return simple_decode_jwt(bearer).get('payload', {}).get('sub', None)
    except:
        pass
    return None


def generate_identifier(short = False) -> str:
    i = uuid4().hex
    return i[:8] if short else i
