import requests
from requests_aws4auth import AWS4Auth
import argparse
import boto3

parser = argparse.ArgumentParser()
parser.add_argument("--es_host", type=str, required=True)
parser.add_argument("--region", type=str, required=True)
parser.add_argument("--backup_role_arn", type=str, required=True)
parser.add_argument("--backup_bucket", type=str, required=True)
parser.add_argument("--snapshot_repo", type=str, required=True)

args, unknown = parser.parse_known_args()
es_host = args.es_host
region = args.region
backup_role_arn = args.backup_role_arn
backup_bucket = args.backup_bucket
snapshot_repo = args.snapshot_repo

service = 'es'
credentials = boto3.Session().get_credentials()
awsauth = AWS4Auth(credentials.access_key, credentials.secret_key, region, service, session_token=credentials.token)

# To Register the repository

path = f'_snapshot/{snapshot_repo}'
url = es_host + "/" + path

payload = {
    "type": "s3",
    "settings": {
        "bucket": f"{backup_bucket}",
        "base_path": f"{snapshot_repo}",
        "region": f"{region}",
        "role_arn": f"{backup_role_arn}"
    }
}

headers = {"Content-Type": "application/json"}

r = requests.put(url, auth=awsauth, json=payload, headers=headers, timeout=300)

print(r.status_code)
print(r.text)