#!/bin/bash
year=$1
#month=$2
while IFS= read -r line
do
   array=($line)
   index=${array[2]}
   echo "Updating index $index"
   curl -XPOST cp-search-elk.default.svc.cluster.local:30091/$index/_update_by_query -H 'Content-Type: application/json' -d'
{
  "script": {
    "source": "ctx._source.compute_cost = ctx._source.cost",
    "lang": "painless"
  },
  "query": {
    "match_all": {}
  }
}'
done < <(curl -XGET cp-search-elk.default.svc.cluster.local:30091/_cat/indices -s | grep  cp-billing-pipeline-run-$year-)