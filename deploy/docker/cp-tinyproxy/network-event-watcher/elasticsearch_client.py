from elasticsearch5 import Elasticsearch
from elasticsearch5 import helpers
import logging

CP_ES_NETEVENT_MAPPING = {
    "netevent": {
        "properties": {
            "reporter": {
                "type": "text"
            },
            "timestamp": {
                "type": "date"
            },
            "host_name": {
                "type": "text"
            },
            "host_ip": {
                "type": "text"
            },
            "runid": {
                "type": "integer"
            },
            "resource": {
                "type": "text"
            },
            "resource_host": {
                "type": "text"
            },
            "method": {
                "type": "text"
            }
        }
    }
}

CP_ES_DOCUMENT_TYPE = "netevent"
CP_ES_NETEVENT_INDEX_POSTFIX = "-{now/m{yyyy.MM.dd}}-000001"

class ElasticSearchClient:

    def __init__(self, elasticsearch_host, index_alias, batch_size=16):
        self.batch = []
        self.batch_size = batch_size
        self.index_alias = index_alias
        self.elasticsearch_host = elasticsearch_host
        self.client = Elasticsearch(elasticsearch_host)
        self.crete_index_if_not_exists()

    def crete_index_if_not_exists(self):
        if not self.client.cat.aliases(name=self.index_alias):
            mappings = CP_ES_NETEVENT_MAPPING
            # use '<' and '>' to enable elk to name index according to the current date and rollover it each day
            index_name = "<" + self.index_alias + CP_ES_NETEVENT_INDEX_POSTFIX + ">"
            self.client.indices.create(
                index=index_name,
                body={
                    "mappings": mappings,
                    "aliases": {
                        self.index_alias: {}
                    }
                }
            )

    def send_event(self, network_event):
        if len(self.batch) < self.batch_size:
            self.batch.append(network_event)
            return
        helpers.bulk(self.client, self.generate_elk_docs())
        self.batch = []

    def generate_elk_docs(self):
        logging.debug("Sending network events: {}".format(self.batch))
        _index = {"_index": self.index_alias, "_type": CP_ES_DOCUMENT_TYPE}
        for e in self.batch:
            yield  {**_index, **e._asdict()}
