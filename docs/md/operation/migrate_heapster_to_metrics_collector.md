# Replace heapster with cp-metrics-collector

For a deployment on Kubernetes 1.15 that runs `cp-heapster` and `cp-heapster-elk`. Run the commands on the Kubernetes master, as `root`.

The collector writes into the same `heapster-YYYY.MM.dd` indices. It first runs beside heapster under another index name, for a comparison. Then heapster is stopped, and the collector writes in its place.

```bash
export VERSION=0.25.0.1234    # the platform build to take the collector image from
export REF=develop            # the git ref of that build, to take the specs from
```

## 1. Check the deployment

```bash
kubectl get deploy cp-heapster cp-heapster-elk
kubectl get nodes -l cloud-pipeline/cp-heapster=true -o name
kubectl get cm cp-config-global -o jsonpath='{.data.CP_HEAPSTER_ELK_INTERNAL_HOST}:{.data.CP_HEAPSTER_ELK_INTERNAL_PORT} {.data.CP_HEAPSTER_METRIC_RESOLUTION}{"\n"}'
```

Expected: both deployments `1/1`, one node, and the address and resolution of heapster's Elasticsearch:

```
NAME              READY   UP-TO-DATE   AVAILABLE   AGE
cp-heapster       1/1     1            1           ...
cp-heapster-elk   1/1     1            1           ...
node/<master node>
cp-heapster-elk.default.svc.cluster.local:30094 30s
```

## 2. Get the specs and fill them in

The image comes from the registry `cp-heapster-elk` was pulled from.

```bash
mkdir -p ~/cp-metrics-collector && cd ~/cp-metrics-collector
curl -sfLO https://raw.githubusercontent.com/epam/cloud-pipeline/$REF/deploy/contents/k8s/cp-metrics-collector/cp-metrics-collector-sa.yaml
curl -sfLO https://raw.githubusercontent.com/epam/cloud-pipeline/$REF/deploy/contents/k8s/cp-metrics-collector/cp-metrics-collector-dpl.yaml
IMAGE=$(kubectl get deploy cp-heapster-elk -o jsonpath='{.spec.template.spec.containers[0].image}')
REPO=${IMAGE%:*}
sed -e "s#\${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline#$REPO#" -e "s#\${CP_HEAPSTER_VERSION}#$VERSION#" \
    -e 's#\${CP_METRICS_COLLECTOR_INDEX}#cp-metrics-collector#' cp-metrics-collector-dpl.yaml > dpl-beside.yaml
sed -e "s#\${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline#$REPO#" -e "s#\${CP_HEAPSTER_VERSION}#$VERSION#" \
    -e 's#\${CP_METRICS_COLLECTOR_INDEX}##' cp-metrics-collector-dpl.yaml > dpl-switch.yaml
grep -H -E 'image:|value:' dpl-beside.yaml dpl-switch.yaml
```

Expected:

```
dpl-beside.yaml:          image: <registry>/lifescience/cloud-pipeline:metrics-collector-<VERSION>
dpl-beside.yaml:            value: "cp-metrics-collector"
dpl-switch.yaml:          image: <registry>/lifescience/cloud-pipeline:metrics-collector-<VERSION>
dpl-switch.yaml:            value: ""
```

## 3. Start it beside heapster

```bash
kubectl apply -f cp-metrics-collector-sa.yaml
kubectl apply -f dpl-beside.yaml
kubectl rollout status deploy/cp-metrics-collector
# a minute later
kubectl logs deploy/cp-metrics-collector --tail=2
```

Expected:

```
serviceaccount/cp-metrics-collector created
clusterrole.rbac.authorization.k8s.io/cp-metrics-collector created
clusterrolebinding.rbac.authorization.k8s.io/cp-metrics-collector created
deployment.apps/cp-metrics-collector created
deployment "cp-metrics-collector" successfully rolled out
... INFO metrics_collector: Batch 1795186410: 1 of 1 nodes scraped, 89 documents written, 0 rejected, in 0.3 s
... INFO metrics_collector: Batch 1795186440: 1 of 1 nodes scraped, 89 documents written, 0 rejected, in 0.1 s
```

Expected: `N of N nodes scraped` (every Ready node) and `0 rejected`. A pod in `ImagePullBackOff` means the registry has no collector image of that `VERSION`.

## 4. Compare, after 30 minutes

```bash
kubectl exec deploy/cp-metrics-collector -- python3 -m metrics_collector.compare --right cp-metrics-collector --minutes 30 --threshold 10
```

Expected: no `node` row. `pod` rows only for pods that use a few millicores (rounding), and for a pod's memory, which is lower by the pause container that heapster counted (about 0.3 MB):

```
view          entity                                    metric             heapster cp-metrics-colle   diff %
pod cpu       <node>/kube-scheduler-<node>              cpu/usage_rate         0.05          0.03333    -33.3
pod memory    <node>/<pod>                              memory/working_set  1223543           904602    -26.1
```

Any other row, a `node` row above all: stop here and keep heapster.

## 5. Switch

```bash
kubectl scale deploy cp-heapster --replicas=0
kubectl apply -f dpl-switch.yaml
kubectl rollout status deploy/cp-metrics-collector
# a minute later
kubectl get deploy cp-heapster cp-metrics-collector
kubectl logs deploy/cp-metrics-collector | grep -E 'Collecting|Batch' | sed -n '1p;$p'
```

Expected:

```
deployment.extensions/cp-heapster scaled
deployment.apps/cp-metrics-collector configured
deployment "cp-metrics-collector" successfully rolled out
NAME                   READY   UP-TO-DATE   AVAILABLE   AGE
cp-heapster            0/0     0            0           ...
cp-metrics-collector   1/1     1            1           ...
... INFO metrics_collector: Collecting every 30 s from https://10.96.0.1:443 into http://cp-heapster-elk.default.svc.cluster.local:30094, index heapster-YYYY.MM.dd
... INFO metrics_collector: Batch 1795188480: 1 of 1 nodes scraped, 83 documents written, 0 rejected, in 0.1 s
```

`cp-heapster` stays, at 0 replicas: `vm-monitor` reports nothing for it, and the rollback only scales it back.

## 6. Remove the comparison indices

```bash
ES=$(kubectl get svc cp-heapster-elk -o jsonpath='{.spec.clusterIP}:{.spec.ports[0].port}')
curl -s -XDELETE "http://$ES/cp-metrics-collector-*"; echo
curl -s -XDELETE "http://$ES/_template/cp-metrics-collector-cp-metrics-collector"; echo
curl -s "http://$ES/_cat/indices/heapster-*,cp-metrics-collector-*?h=index"
```

Expected: two acknowledgements, and only the `heapster-*` indices left:

```
{"acknowledged":true}
{"acknowledged":true}
heapster-2026.11.20
```

## Rollback

```bash
kubectl delete deploy cp-metrics-collector
kubectl scale deploy cp-heapster --replicas=1
kubectl rollout status deploy/cp-heapster
```

Expected:

```
deployment.extensions "cp-metrics-collector" deleted
deployment.extensions/cp-heapster scaled
deployment "cp-heapster" successfully rolled out
```

## After a pipectl run

A `pipectl` run that installs `cp-heapster` creates it again with 1 replica, and leaves `cp-metrics-collector` running. Scale `cp-heapster` to 0 again: `kubectl scale deploy cp-heapster --replicas=0`.

## A deployment without heapster

Steps 2 and 3, with `dpl-switch.yaml` in place of `dpl-beside.yaml`, and `IMAGE` from another platform deployment, as all the platform images come from one repository: `IMAGE=$(kubectl get deploy cp-api-srv -o jsonpath='{.spec.template.spec.containers[0].image}')`.
