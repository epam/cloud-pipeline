"""CLI entry: `python -m cloud_pipeline_mcp_remote` or `cloud-pipeline-mcp-remote`."""

from __future__ import annotations

import argparse
import logging
import os

import uvicorn

from cloud_pipeline_mcp_remote.app import create_app


def main() -> None:
    default_host = os.environ.get("HOST", "0.0.0.0")
    default_port = int(os.environ.get("PORT", "8080"))
    p = argparse.ArgumentParser(description="Cloud Pipeline remote MCP (Streamable HTTP)")
    p.add_argument("--host", default=default_host)
    p.add_argument("--port", type=int, default=default_port)
    p.add_argument("--log-level", default=os.environ.get("LOG_LEVEL", "info"))
    args = p.parse_args()
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    app = create_app()
    uvicorn.run(app, host=args.host, port=args.port, log_level=args.log_level.lower())


if __name__ == "__main__":
    main()
