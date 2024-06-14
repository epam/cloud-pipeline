# Сomparison of performance for OpenMPI and OpenMPI with EFA

- [Performance](#performance-comparison)
    - [Synthetic data experiment](#synthetic-data-experiment)
    - [Real data experiment](#real-data-experiment)
- [Costs](#costs)

## Performance comparison

The performance was measured for:
- OpenMPI with default network capabilities 
- OpenMPI + EFA (https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/efa.html):

A performance comparison of Cloud Pipeline storages was conducted against synthetic and real data.  
All experiments were carried out on `c5n.18xlarge` (**72** CPU, **192** RAM) `AWS` instance.

### Synthetic data experiment

In this experiment we have used one set of standard MPI performance tests (https://github.com/intel/opa-mpi-apps/):

- IMB-MPI1
  - PingPong
  - Sendrecv
  - Exchange
  - Allreduce
  - Reduce
  - Reduce_scatter
  - Allgather
  - Allgatherv
  - Gather
  - Gatherv
  - Scatter
  - Scatterv
  - Alltoall
  - Alltoallv
  - Bcast
  - Barrier
  
#### Results

|  | OMPI | OMPI + EFA | Performance gain|
|---|---|---|---|
| PingPong | t[usec] 980.27<br>Mbytes/sec 4278.71 | t[usec] 372.02<br>Mbytes/sec 11274.49 | 2.6
| Sendrecv | t[usec] 6714.98<br>Mbytes/sec 1142.18 |  t[usec] 2832.33<br>Mbytes/sec 2450.30 | 2.4
| Exchange | t[usec] 12924.25<br>Mbytes/sec 1186.70 | t[usec] 5120.58<br>Mbytes/sec 2901.01 |2.5
| Allreduce | t[usec] 16984.99 | t[usec] 13322.22 | 1.3
| Reduce | t[usec] 10550.72 | t[usec] 5727.75 | 1.84
| Reduce_scatter | t[usec] 10013.94 | t[usec] 17702.15 | 0.58
| Allgather | t[usec] 473262.95 | t[usec] 249779.37 | 1.9
| Allgatherv | t[usec] 474221.24 | t[usec] 249236.40 | 1.9
| Gather | t[usec] 44816.74 | t[usec] 20622.56 | 2.17
| Gatherv | t[usec] 63221.96 | t[usec] 42998.62 | 1.47
| Scatter | t[usec] 46679.99 | t[usec] 36459.75 | 1.28
| Scatterv | t[usec] 46861.19 | t[usec] 44070.43 | 1.06
| Alltoall | t[usec] 2352516.14 | t[usec] 573233.56 | 4.1
| Alltoallv | t[usec] 2386733.17 | t[usec] 574051.19 | 4.1
| Bcast | t[usec] 12662.27 | t[usec] 13795.77 | 0.91
| Barrier | t[usec] 206.22 | t[usec]  99.70 | 2.1


### Real data experiment

The **`nonmem production md`** command was used to conduct an experiment with real data.  
The input data: PDB-file `https://www.rcsb.org/3d-view/1MQL`

For this benchmark was used `nonmem` pipeline, that contains next steps:

- Solvation
- Energy minimization
- Equilibration MD run 1
- Equilibration MD run 2
- Production MD run

For benchmark result we used only production md step:

``` 
mpirun --allow-run-as-root --hostfile /common/hostfile -x PATH=$PATH mdrun_mpi -deffnm  productionrun_1
```

The experimental run result is presented in the following tables:

#### production_md modelation steps: 50000
|  | OMPI | OMPI + EFA | Performance gain|
|---|---|---|---|
| 2 node<br> c5n.18xlarge|8m29.338s|7m35.730s|1.12|
| 4 node<br> c5n.18xlarge|6m16.000s|4m51.415s|1.29|

#### production_md modelation steps: 100000
|  | OMPI | OMPI + EFA | Performance gain|
|---|---|---|---|
| 2 node<br> c5n.18xlarge|16m50.462s|15m39.143s| 1.07|
| 4 node<br> c5n.18xlarge|12m34.657s|9m46.148s|1.28|

#### production_md modelation steps: 500000
|  | OMPI | OMPI + EFA | Performance gain|
|---|---|---|---|
| 2 node<br> c5n.18xlarge|78m17.860s|68m54.032s|1.14|
| 4 node<br> c5n.18xlarge|63m48.930s|47m43.562s|1.34|

## Costs

The presence of the EFA interface does not affect the price:

```
EFA is available as an optional EC2 networking feature that you can enable on any supported EC2 instance at no additional cost. 
```
`https://aws.amazon.com/hpc/efa/`