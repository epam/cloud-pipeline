## Development

Use the following env vars to configure dev environment (e.g., using `.env` file):

```shell
MODEL_NAME=gemini-2.5-flash-lite
EMBED_MODEL_NAME=gemini-embedding-001

GOOGLE_API_KEY=...
GITHUB_TOKEN=...

API_TOKEN=...
API=https://cloud-pipeline-server/pipeline/restapi/

CHROMA_DB_PATH=...
CHATS_DB_PATH=...
CHATS_DB_NAME=...
GITHUB_CLONE_TMP_PATH=...
CP_AI_LOGS_DIR=...
```

where

- `GOOGLE_API_KEY` (required): Google API key
- `GITHUB_TOKEN` (required): GitHub token
- `API_TOKEN` (required): Cloud Pipeline API token
- `API` (required): Cloud Pipeline `.../restapi` absolute url
- `MODEL_NAME` (optional, default: `gemini-2.5-flash-lite`): Gemini model name
- `EMBED_MODEL_NAME` (optional, default: `gemini-embedding-001`): Gemini embedding model name
- `CHROMA_DB_PATH` (optional, default: `/api/chromadb`): a path to chromadb database containing documents (GitHub issues & docs)
- `MONGODB_HOST` (optional, default: 127.0.0.1): mondodb database host, e.g
- `MONGODB_PORT` (optional, default: 27017): mongodb database port
- `GITHUB_CLONE_TMP_PATH` (optional, default: `/tmp/repo`): a path to a temp directory to clone cloud-pipeline repo to
- `CPAIBOT_LOGS_DIR` (optional, default: `/var/log`): a directory for storing logs