# Stop and start pipelines during the node start

**Actions**:

1. Check that there are no empty nodes (except master-node)
2. Launch a pipeline
3. Wait for a Cloud instance is booted for the pipeline
4. Stop the pipeline launched at step 2

***

**Expected result**:

- After the pipeline is stopped, a new node still appears
- That node has label with the pipeline run ID launched at step 2
