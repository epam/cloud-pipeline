import os
from pydantic import BaseModel
from cpaibot.common.env import EnvField


class Settings(BaseModel):
    # Google
    GOOGLE_API_KEY: str | None = EnvField.str_env("GOOGLE_API_KEY")
    DEFAULT_MODEL_NAME: str = EnvField.str_env("MODEL_NAME", 'gemini-2.5-flash-lite')
    # EMBED_MODEL_NAME: str = EnvField.str_env("EMBED_MODEL_NAME", 'text-embedding-004')
    EMBED_MODEL_NAME: str = EnvField.str_env("EMBED_MODEL_NAME", 'gemini-embedding-001')
    LLM_MAX_RETRIES: int = EnvField.int_env("LLM_MAX_RETRIES", 3)
    LLM_RETRY_DELAY_SECONDS: int = EnvField.int_env("LLM_RETRY_DELAY_SECONDS", 3)
    # Mongodb
    MONGODB_HOST: str = EnvField.str_env("MONGODB_HOST", "127.0.0.1")
    MONGODB_PORT: int = EnvField.int_env("MONGODB_PORT", 27017)
    MONGODB_DATABASE_NAME: str = EnvField.str_env("MONGODB_DATABASE", "cpaibot")
    # Github
    GITHUB_TOKEN: str | None = EnvField.str_env("GITHUB_TOKEN")
    GITHUB_REPO_OWNER: str = EnvField.str_env('GITHUB_REPO_OWNER', 'epam')
    GITHUB_REPO: str = EnvField.str_env('GITHUB_REPO', 'cloud-pipeline')
    GITHUB_REPO_DOCS_PATH: str = EnvField.str_env('GITHUB_REPO_DOCS_PATH', 'docs/md')
    GITHUB_REPO_BRANCH: str = EnvField.str_env('GITHUB_REPO_BRANCH', 'develop')
    GITHUB_CLONE_TMP_PATH: str = EnvField.str_env('GITHUB_CLONE_TMP_PATH', '/tmp/repo')
    # Chromadb
    CHROMA_DB_PATH: str = EnvField.str_env('CHROMA_DB_PATH', '/api/chromadb')
    # General
    CP_VERIFY_RESTAPI_CERT: bool = EnvField.bool_env('CP_VERIFY_RESTAPI_CERT', True)
    CP_SUBMIT_ASSISTANT_AS_PROCESS: bool = EnvField.bool_env('CP_SUBMIT_ASSISTANT_AS_PROCESS', False)

    @property
    def CP_API(self) -> str | None:
        return os.environ.get("API")

    @property
    def CP_APPLICATION_URI(self) -> str | None:
        api = self.CP_API
        if api is None:
            return None
        result = api
        if result.lower().endswith('/'):
            result = result[:-1]
        if result.lower().endswith('/restapi'):
            result = result[:-len('restapi')]
        return result

    @property
    def CP_API_TOKEN(self) -> str | None:
        return os.environ.get("API_TOKEN")

    @property
    def CP_RUN_ID(self) -> int | None:
        v = os.environ.get("RUN_ID")
        if v:
            return int(v)
        return None

    @property
    def mongodb_url(self) -> str:
        return f"mongodb://{self.MONGODB_HOST}:{self.MONGODB_PORT}"

    def get_application_entity_url(self, entity_uri: str) -> str | None:
        app_url = self.CP_APPLICATION_URI
        if app_url is None:
            return None
        if not app_url.endswith('/'):
            app_url += '/'
        while entity_uri.startswith('/'):
            entity_uri = entity_uri[1:]
        return f'{app_url}{entity_uri}'


settings = Settings()
