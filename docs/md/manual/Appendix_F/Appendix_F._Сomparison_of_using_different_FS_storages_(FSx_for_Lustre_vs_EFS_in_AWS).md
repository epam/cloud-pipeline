# Сomparison of using different FS storages (FSx for Lustre vs EFS in AWS)

- [Performance](#performance-comparison)
    - [Synthetic data experiment](#synthetic-data-experiment)
    - [Real data experiment](#real-data-experiment)
- [Costs](#costs)

## Performance comparison

A performance comparison of FSx for Lustre and EFS in AWS was conducted against synthetic and real data. 
All experiments were carried out on `c5.2xlarge (8 CPU, 16 RAM)` AWS instance.

### Synthetic data experiment

In this experiment we have generated two types of data:
- 100Gb large file
- 100 000 small files by 500Kb

With this data, we have measured the creation and read times using the `time` command of Unix and Unix-like systems. 

* The command that was used to create a 100Gb large file by 100Mb chunks:

`dd if=/dev/urandom of=/path-to-storage/large_100gb.txt iflag=fullblock bs=100M count=1024`

* The command that was used to create a 100 000 small files by 500Kb:

`
for j in {1..100000}; do 
 	head -c 500kB </dev/urandom > /path-to-storage/small/randfile$j.txt
done
`

* The command that was used to read a large file:

`dd if=/path-to-storage/large_100gb.txt of=/dev/null conv=fdatasync`

* The command that was used to read small files:

`
for file in /path-to-storage/small/* ; do
    dd if=$file of=/dev/null
done
`

The synthetic data experiment was initially carried out in one thread and then 4 and 8 threads.  

The experimental results on synthetic data are presented in the following tables:

#### 1 thread

| Storage | Create the large file | Read the large file | Create many small files | Read many small files |
|---|---|---|---|---|
| **EFS** | real 17m24.812s <br/> user 0m0.004s <br/> sys 10m34.675s | real 17m18.216s <br/> user 0m48.904s <br/> sys 3m9.517s | real 53m3.498s <br/> user 0m58.209s <br/> sys 5m46.386s | real 27m11.831s <br/> user 2m14.295s <br/> sys 1m35.923s |
| **LUSTRE** | real 10m55.494s <br/> user 0m0.004s <br/> sys 9m47.326s | real 13m28.536s <br/> user 0m51.675s <br/> sys 12m36.813s | real 12m37.380s <br/> user 1m54.744s <br/> sys 5m13.759s | real 20m40.877s <br/> user 0m44.095s <br/> sys 9m4.877s | 

#### 4 threads

| Storage | Create the large file | Read the large file | Create many small files | Read many small files |
|---|---|---|---|---|
| **EFS** | real 69m25.583s <br/> user 0m0.015s <br/> sys 11m30.451s | real 64m20.614s <br/> user 0m48.233s <br/> sys 3m15.074s | real 59m18.137s <br/> user 0m37.185s <br/> sys 8m29.882s | real 33m19.459s <br/> user 2m23.134s <br/> sys 2m18.345s |
| **LUSTRE** | real 38m32.383s <br/> user 0m0.014s <br/> sys 36m40.821s | real 20m45.156s <br/> user 0m59.189s <br/> sys 19m26.054s | real 25m38.531s <br/> user 0m21.820s <br/> sys 16m59.318s | real 24m58.620s <br/> user 2m11.449s <br/> sys 11m57.240s |

#### 8 threads

| Storage | Create the large file | Read the large file | Create many small files | Read many small files |
|---|---|---|---|---|
| **EFS** | real 127m49.718s <br/> user 0m0.010s <br/> sys 14m44.358s | real 122m46.188s <br/> user 1m12.786s <br/> sys 21m14.236s | real 72m43.596s <br/> user 0m26.727s <br/> sys 15m0.582s | real 62m46.118s <br/> user 2m31.595s <br/> sys 2m31.577s |
| **LUSTRE** | real 93m56.846s <br/> user 0m0.018s <br/> sys 90m30.5s | real 94m1.258s <br/> user 0m48.908s <br/> sys 3m18.557s | real 50m42.845s <br/> user 0m23.462s <br/> sys 35m12.511s | real 30m53.199s <br/> user 0m54.020s <br/> sys 14m42.712s | 

As we can see from the presented results, Amazon FSx for Lustre is several times faster (from 1.3 to 3.2 times for read mode and from 1.3 to 4 times for write mode) than Amazon EFS.

### Real data experiment

The **cellranger count** pipeline was used to conduct an experiment with real data. 
The input data:
* 15Gb transcriptome reference
* 50Gb fastqs

The command that was used to run **cellranger count** pipeline:

`/path-to-cellranger/3.0.2/bin/cellranger count --localcores=8 --id={id} --transcriptome=/path-to-transcriptome-reference/refdata-cellranger-mm10-3.0.0 --chemistry=SC5P-R2 --fastqs=/path-to-fastqs/fastqs_test --sample={sample_name}`

The experimental run result is presented in the following table:

| Storage | Execution time |
|---|---|
| **EFS** | real 207m15.566s <br/> user 413m32.168s <br/> sys 13m40.581s |
| **LUSTRE** | real 189m23.586s <br/> user 434m6.950s <br/> sys 13m2.902s |

In this case, Amazon FSx for Lustre was faster Amazon EFS just to ~ 9%. 

## Costs

Cost calculations have been performed in according to Amazon pricing at the time of this document.
For the experiments were used the Lustre and EFS storages that were created in the US East (N.Virginia) region with similar features:

| Storage | Storage size | Throughput mode |
|---|---|---|
| **EFS** | Size in EFS Standard: 1 TiB (100%) | Bursting: 50 MB/s/TiB  |
| **LUSTRE** | SSD: 1.2 TiB Capacity | 50 MB/s/TiB baseline, up to 1.3 GB/s/TiB burst |

The total charge for the month of usage for FSx for Lustre and EFS Standard Storage is calculated in different ways:

* **EFS** <br/>

  > 1. 1 TB per month x 1024 GB in a TB = 1024 GB per month (data stored in Standard storage) <br/>
  > 2. 1 024 GB per month x $0,30 GB-month = **$307,20** (Standard Storage monthly cost)

* **FSx for Lustre** <br/>

  > 1. $0.14 GB-month / 30 / 24 = $0.000194 GB-hour <br/>
  > 2. 1228 GB x $0.000194 GB-hour x 720 hours = **$171,5** (FSx for Lustre monthly cost)
     
As seen from the calculation, EFS monthly cost is more expensive than Amazon FSx for Lustre monthly cost in 1.8 times for similar features storage at the time of this document.
