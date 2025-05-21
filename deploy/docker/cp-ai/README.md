## Development

Use the following env vars to configure dev environment (e.g., using `.env` file):

```shell
GOOGLE_GENAI_MODEL=gemini-2.0-flash-lite
EMBED_MODEL_NAME=text-embedding-004

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
- `GOOGLE_GENAI_MODEL` (optional, default: `gemini-2.0-flash-lite`): Gemini model name
- `EMBED_MODEL_NAME` (optional, default: `text-embedding-004`): Gemini embedding model name
- `CHROMA_DB_PATH` (optional, default: `/api/chromadb`): a path to chromadb database containing documents (GitHub issues & docs)
- `CHATS_DB_PATH` (optional): a url to mondodb database, e.g. `mongodb://localhost:27017/`
- `CHATS_DB_NAME` (optional, default: `chatbot-db`): a mongodb database name
- `GITHUB_CLONE_TMP_PATH` (optional, default: `/tmp/repo`): a path to a temp directory to clone cloud-pipeline repo to
- `CP_AI_LOGS_DIR` (optional, default: `/var/log`): a directory for storing logs