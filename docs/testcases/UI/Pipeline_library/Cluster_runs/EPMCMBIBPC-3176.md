# [MANUAL] Validation of Kubernetes cluster

*Note: For the pipeline, only a Centos-based image should be used.*

Test verifies launching a Kubernetes cluster: the master/worker setup tasks, and that `kubectl` works from the master node against the running child node.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-8 of the [EPMCMBIBPC-975](EPMCMBIBPC-975.md) case |  |
| 2 | Set the **Enable Kubernetes** checkbox |  |
| 3 | Click the **OK** button | The name of the **Configure cluster** control becomes **Kubernetes Cluster (1 child node)** |
| 4 | Expand the **Advanced** section |  |
| 5 | Set the **Start idle** checkbox |  |
| 6 | Click the **Launch** button |  |
| 7 | Click the **Launch** button in the appeared pop-up window |  |
| 8 | Click the **+** button near the just-launched pipeline name at the **ACTIVE RUNS** tab |  |
| 9 | Save the RunIDs of the parent and the child pipeline runs |  |
| 10 | Click the just-launched pipeline name on the **ACTIVE RUNS** tab |  |
| 11 | Expand the **Parameters** section | Text appears that contains:<br>`CP_CAP_KUBE: true`<br>`CP_CAP_DIND_CONTAINER: true`<br>`CP_CAP_SYSTEMD_CONTAINER: true` |
| 12 | Wait until the **SSH** button appears |  |
| 13 | Click the **KubeMasterSetup** task | The log window contains the row `Kubernetes master is started` |
| 14 | Click the **KubeMasterSetupWorkers** task | The log window contains the row `All worker nodes are connected` |
| 15 | Click the **SSH** button |  |
| 16 | In the opened tab, run the command `kubectl cluster-info` | The command output contains text `Kubernetes master is running at <...>` and `KubeDNS is running at <...>` |
| 17 | Run the command `kubectl get nodes` | The command output contains a table with columns **NAME**, **STATUS**, **ROLES**, **AGE**, **VERSION** and 2 data rows: the master node (**NAME** = `<master_RunID>`, **STATUS** = `Ready`, **ROLES** = `master`, where `<master_RunID>` is the RunID of the parent run saved at step 9), and the child node (**NAME** = `<child_RunID>`, **STATUS** = `Ready`, **ROLES** = `<none>`, where `<child_RunID>` is the RunID of the nested run saved at step 9) |
| 18 | Run the command `kubectl run test-nginx --image=nginx` |  |
| 19 | Run the command `kubectl get pods --output=wide` | The command output contains a table with columns **NAME**, **READY**, **STATUS**, **RESTARTS**, **AGE**, **IP**, **NODE**, **NOMINATED NODE**, **READINESS GATES** and 1 data row with **NAME** = `test-nginx-<...>`, **STATUS** = `Running`, **NODE** = `<child_RunID>` (the RunID of the nested run saved at step 9) |
| 20 | Run the command `docker --version` | The command output contains text `Docker version <...>, build <...>` |
| 21 | Run the command `systemctl --version` | The command output contains text `systemd <...>` |

**After**:
- Stop the child run first, then the parent run launched at step 7
