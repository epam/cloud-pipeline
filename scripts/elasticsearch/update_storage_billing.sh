#!/bin/bash
year=$1
while IFS= read -r line
do
   array=($line)
   index=${array[2]}
   echo "Updating index $index"
   curl -XPOST cp-search-elk.default.svc.cluster.local:30091/$index/_update_by_query -H 'Content-Type: application/json' -d'
{
  "script": {
    "source": "ctx._source.standard_cost = ctx._source.cost; ctx._source.standard_total_cost = ctx._source.cost; ctx._source.standard_usage_bytes = ctx._source.usage_bytes; ctx._source.standard_total_usage_bytes = ctx._source.usage_bytes; ctx._source.standard_ov_cost = 0; ctx._source.standard_ov_usage_bytes = 0",
    "lang": "painless"
  },
  "query": {
    "match_all": {}
  }
}'
done < <(curl -XGET cp-search-elk.default.svc.cluster.local:30091/_cat/indices -s | grep  cp-billing-storage-$year-)