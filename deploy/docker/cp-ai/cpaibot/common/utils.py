import re
import json
import base64


def extract_json_response(response: str):
    if not response:
        return None
    resp = response
    resp = re.sub(r'^\s*`+(json)?|`+\s*$', '', resp, flags=re.DOTALL).strip()
    resp = re.sub(r'^\s*`+|`+\s*$', '', resp, flags=re.DOTALL).strip()
    try:
        return json.loads(resp)
    except BaseException:
        return None


def extract_quoted_response(response: str, flags: int = re.DOTALL | re.MULTILINE | re.IGNORECASE) -> str:
    resp = re.match(r'^["\'`](.+)["\'`]$', response.strip(), flags=flags)
    return resp.group(1) if resp else response


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