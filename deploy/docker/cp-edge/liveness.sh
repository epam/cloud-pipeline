#!/bin/bash

function die {
    echo "Endpoint didn't repspond with HTTP 200: $1"
    exit 1
}

EDGE_HEALTH_ENDPOINT="http://127.0.0.1:8888/edge-health"
WETTY_HEALTH_ENDPOINT="http://127.0.0.1:8888/wetty-health"

curl --fail "$EDGE_HEALTH_ENDPOINT" > /dev/null 2>&1 || die "$EDGE_HEALTH_ENDPOINT"
curl --fail "$WETTY_HEALTH_ENDPOINT" > /dev/null 2>&1 || die "$WETTY_HEALTH_ENDPOINT"
