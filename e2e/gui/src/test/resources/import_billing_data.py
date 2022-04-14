import argparse
import requests
import json
from datetime import datetime
from datetime import date, timedelta

run_mapping = {"_doc": {"properties": {"doc_type": { "type": "keyword", "store": True },"run_id": { "type": "keyword", "store": True },"pipeline": { "type": "keyword" },"pipeline_name": { "type": "keyword" },"resource_type": { "type": "keyword" },"compute_type": { "type": "keyword" },"cost": {"type": "long"},"run_price": {"type": "long"},"usage_minutes": {"type": "long"},"owner": { "type": "keyword" },"groups": { "type": "keyword" },"tool": {"type": "keyword"},"instance_type":  { "type": "keyword" },"billing_center":  { "type": "keyword" },"cloudRegionId": { "type": "keyword" },"created_date": { "type": "date" },"started_date": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss.SSS" },"finished_date": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss.SSS" }}}}
storage_mapping = {"_doc": {"properties": {"doc_type": { "type": "keyword", "store": True },"storage_id": { "type": "keyword", "store": True },"resource_type": { "type": "keyword" }, "cloudRegionId": { "type": "keyword" },"provider": { "type": "keyword" },"storage_type": { "type": "keyword" },"owner": { "type": "keyword" },"groups": { "type": "keyword" },"billing_center":  { "type": "keyword" },        "usage_bytes": {"type": "long"},"cost": {"type": "long"},"created_date": {"type": "date"}}}}

class ElasticClient(object):

    def __init__(self, url):
        self.url = url.rstrip('/')
        self._headers = {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
        }

    def bulk_insert(self, docs):
        data = ''
        for index, doc in docs.iteritems():
            mapping = storage_mapping if index.startswith('cp-billing-storage') else run_mapping
            self.create_index(index, mapping)
            data = data + '{"index": {"_index": "%s", "_type" : "_doc", "_id": "%d"}}\n' % (index, doc.get('id')) + json.dumps(doc) + '\n'
        self._post('/_bulk', str_data=data)

    def bulk_delete(self, requests):
        data = ''
        for index, id in requests.iteritems():
            data = data + '{ "delete" : { "_index" : "%s", "_type" : "_doc", "_id" : "%s" } }\n' % (index, str(id))
        self._post('/_bulk', str_data=data)

    def create_index(self, index, mapping):
        response = self._get('/' + index)
        if not response.ok:
            data = {
                "settings": {
                    "number_of_shards": 1
                },
                "mappings": mapping
            }
            self._put('/' + index, data=data)

    def insert_doc(self, index, id, doc):
        self._put('/%s/_doc/%d' % (index, id), data=doc)

    def delete_doc(self, index, id):
        self._delete('/%s/_doc/%d' % (index, id))

    def _get(self, url):
        return self._execute_request(url, 'get')

    def _put(self, url, data=None):
        return self._execute_request(url, 'put', data=data)

    def _post(self, url, data=None, str_data=None):
        return self._execute_request(url, 'post', data=data, str_data=str_data)

    def _delete(self, url, data=None):
        return self._execute_request(url, 'delete', data=data)

    def _execute_request(self, method_url, http_method, data=None, str_data=None):
        url = self.url + method_url
        print('Calling %s...' % url)
        request_data = None
        if data:
            request_data = json.dumps(data)
        if str_data:
            request_data = str_data
        response = requests.request(method=http_method, headers=self._headers,
                                    url=url, data=request_data, verify=False)
        print('Result: ' + str(response.json()))
        return response


class Entry(object):

    def __init__(self, user, billing_center, groups, type, sum, start_date, end_date, id):
        self.user = user
        self.billing_center = billing_center
        self.groups = groups
        self.type = type
        self.sum = sum
        self.start_date = start_date
        self.end_date = end_date
        self.id = id


def build_docs(entry):
    docs = {}

    index_name = 'storage' if entry.type == 'OBJECT_STORAGE' or entry.type == 'FILE_STORAGE' else 'pipeline-run'
    index_prefix = 'cp-billing-storage-' if index_name == 'storage' else 'cp-billing-pipeline-run-'
    delta = timedelta(days=1)
    days = max(1, (entry.end_date - entry.start_date).days)
    cost_per_day = entry.sum/days
    start_date = entry.start_date
    current_expense = 0
    while start_date < entry.end_date:
        current_date = start_date.strftime("%Y-%m-%d")
        index = index_prefix + current_date
        start_date += delta
        current_expense += cost_per_day
        if start_date == entry.end_date:
            cost_per_day = cost_per_day + entry.sum - current_expense

        if index_name == 'storage':
            doc = {
                'doc_type': 'S3_STORAGE' if entry.type == 'OBJECT_STORAGE' else 'NFS_STORAGE',
                'storage_id': entry.id,
                'id': entry.id,
                'resource_type': 'STORAGE',
                'cloudRegionId': 1,
                'provider': 'AWS',
                'storage_type': entry.type,
                'usage_bytes': 1000,
                'cost': cost_per_day,
                'created_date': current_date,
                "owner": entry.user,
                "groups": entry.groups,
                "billing_center": entry.billing_center
            }
        else:
            doc = {
                'doc_type': 'PIPELINE_RUN',
                'id': entry.id,
                'run_id': entry.id,
                'resource_type': 'COMPUTE',
                'tool': 'docker.aws.cloud-pipeline.com:443/library/centos:latest',
                'pipeline': None,
                'pipelineName': None,
                'instance_type': 'm5.large',
                'compute_type': entry.type,
                'cost': cost_per_day,
                'usage_minutes': 10,
                'paused_minutes': 10,
                'run_price': 10,
                'compute_price': 8,
                'disk_price': 2,
                'cloudRegionId': 1,
                'created_date': current_date,
                "owner": entry.user,
                "groups": entry.groups,
                "billing_center": entry.billing_center
            }
        docs[index] = doc
    return docs


def add_data(data, es_client):
    for entry in data:
        docs = build_docs(entry)
        es_client.bulk_insert(docs)


def build_delete_requests(entry):
    docs = {}

    index_name = 'storage' if entry.type == 'OBJECT_STORAGE' or entry.type == 'FILE_STORAGE' else 'pipeline-run'
    index_prefix = 'cp-billing-storage-' if index_name == 'storage' else 'cp-billing-pipeline-run-'
    delta = timedelta(days=1)
    start_date = entry.start_date
    while start_date < entry.end_date:
        current_date = start_date.strftime("%Y-%m-%d")
        index = index_prefix + current_date
        start_date += delta
        docs[index] = entry.id
    return docs


def remove_data(data, es_client):
    for entry in data:
        requests = build_delete_requests(entry)
        es_client.bulk_delete(requests)


def parse_date(text):
    return datetime.strptime(text, '%Y-%m-%d')


def parse_file(file_path):
    entries = []
    with open(file_path, 'r') as input:
        header = True
        for line in input.readlines():
            if header:
                header = False
                continue
            chunks = line.split('\t')
            entries.append(Entry(chunks[0],
                                 chunks[1],
                                 chunks[2].split(','),
                                 chunks[3],
                                 float(chunks[4]) * 10000,
                                 parse_date(chunks[5]),
                                 parse_date(chunks[6]) + timedelta(days=1),
                                 int(chunks[7])))
    return entries


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--operation', required=True)
    parser.add_argument('--data-file', required=True)
    parser.add_argument('--elastic-url', required=True)
    args = parser.parse_args()
    data = parse_file(args.data_file)
    if args.operation == 'add':
        add_data(data, ElasticClient(args.elastic_url))
    elif args.operation == 'remove':
        remove_data(data, ElasticClient(args.elastic_url))
    else:
        print('Invalid operation %s. Supported values: add, remove.' % args.operation)


if __name__ == '__main__':
    main()
