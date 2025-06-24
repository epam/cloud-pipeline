import os
from dotenv import load_dotenv
from pydantic import BaseModel

load_dotenv()

def _get_application_url_from_api(api: str | None) -> str | None:
    if api is None:
        return None
    result = api
    if result.lower().endswith('/'):
        result = result[:-1]
    if result.lower().endswith('/restapi'):
        result = result[:-len('restapi')]
    return result


_cp_api: str | None = os.environ.get('API', None)

def _get_env(key: str, default_value):
    """Utility method for fetching env var.
    If env var is set to "" (empty string), use default value
    """
    value = os.environ.get(key, default_value)
    if isinstance(value, str) and value.strip() == '':
        return default_value
    return value

class CpAiSettings(BaseModel):
    # Model settings
    GOOGLE_API_KEY: str | None = os.environ.get('GOOGLE_API_KEY', None)
    GOOGLE_GENAI_MODEL: str = _get_env('GOOGLE_GENAI_MODEL', 'gemini-2.0-flash-lite')
    EMBED_MODEL_NAME: str = _get_env('EMBED_MODEL_NAME', 'text-embedding-004')
    # --------------

    # Cloud Pipeline settings
    CP_API_TOKEN: str | None = os.environ.get('API_TOKEN', None)
    CP_API: str | None = _cp_api
    CP_APPLICATION_URI: str | None = _get_application_url_from_api(_cp_api)
    # -----------------------

    # GITHUB settings
    GITHUB_TOKEN: str | None = os.environ.get('GITHUB_TOKEN', None)
    GITHUB_REPO_OWNER: str = os.environ.get('GITHUB_REPO_OWNER', 'epam')
    GITHUB_REPO: str = os.environ.get('GITHUB_REPO', 'cloud-pipeline')
    GITHUB_REPO_DOCS_PATH: str = os.environ.get('GITHUB_REPO_DOCS_PATH', 'docs/md')
    GITHUB_REPO_BRANCH: str = os.environ.get('GITHUB_REPO_BRANCH', 'develop')
    GITHUB_CLONE_TMP_PATH: str = os.environ.get('GITHUB_CLONE_TMP_PATH', '/tmp/repo')
    # ---------------

    # Databases settings
    CHROMA_DB_PATH: str | None =os.environ.get('CHROMA_DB_PATH', '/api/chromadb')
    CHATS_DB_PATH: str | None = os.environ.get('CHATS_DB_PATH', None)
    CHATS_DB_NAME: str = os.environ.get('CHATS_DB_NAME', 'chatbot-db')
    # ------------------

    # General settings
    CP_AI_LOGS_DIR: str = os.environ.get('CP_AI_LOGS_DIR', '/var/log')
    _CP_AI_API_LOGS: str | None = os.environ.get('CP_AI_API_LOGS', None)
    _CP_AI_AGENT_LOGS: str | None = os.environ.get('CP_AI_AGENT_LOGS', None)
    _CP_AI_DOCUMENTS_LOGS: str | None = os.environ.get('CP_AI_DOCUMENTS_LOGS', None)
    _CP_AI_CP_RESTAPI_LOGS: str | None = os.environ.get('CP_AI_CP_RESTAPI_LOGS', None)

    CP_VERIFY_RESTAPI_CERT: bool | str = os.environ.get('CP_VERIFY_RESTAPI_CERT', True)
    # ----------------

    @property
    def cp_api_logs_file(self) -> str:
        if self._CP_AI_API_LOGS is not None:
            return self._CP_AI_API_LOGS
        return os.path.join(self.CP_AI_LOGS_DIR, 'api.log')

    @property
    def cp_agents_logs_file(self) -> str:
        if self._CP_AI_AGENT_LOGS is not None:
            return self._CP_AI_AGENT_LOGS
        return os.path.join(self.CP_AI_LOGS_DIR, 'agent.log')

    @property
    def cp_documents_logs_file(self) -> str:
        if self._CP_AI_DOCUMENTS_LOGS is not None:
            return self._CP_AI_DOCUMENTS_LOGS
        return os.path.join(self.CP_AI_LOGS_DIR, 'documents.log')

    @property
    def cp_restapi_logs_file(self) -> str:
        if self._CP_AI_CP_RESTAPI_LOGS is not None:
            return self._CP_AI_CP_RESTAPI_LOGS
        return os.path.join(self.CP_AI_LOGS_DIR, 'restapi.log')

    @property
    def verify_restapi_cert(self):
        value = self.CP_VERIFY_RESTAPI_CERT
        if value is not None:
            if isinstance(value, bool):
                return value
            return str(value).lower() in {'true', 'yes'}
        return True

cp_ai_settings = CpAiSettings()
