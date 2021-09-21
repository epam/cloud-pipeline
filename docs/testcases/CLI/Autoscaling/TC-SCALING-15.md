# Check that minimal count of nodes always work

**Actions**:

1. Set the System Preference `cluster.min.size` value as `1`
2. Launch a pipeline
3. Stop the pipeline launched at step 2
4. Wait 1 hour, don't launch any pipelines

***

**Expected result**:

After an hour:

- There is 1 Cloud instance booted (except instance for the master-node)
- There is 1 node in the cluster (except master-node)
