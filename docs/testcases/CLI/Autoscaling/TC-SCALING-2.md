# Node reassign

**Actions**:

1. Check that there are no empty nodes (except master-node)
2. Launch a pipeline
3. Wait for a new node assigned to the pipeline launched at step 2
4. Save the `machineID` value of the node from step 3
5. Stop the pipeline launched at step 2
6. Launch a pipeline with the same instance type as at step 2
7. Wait the node is assigned to the pipeline launched at step 6

***

**Expected result**:

- The `machineID` value of the node from step 7 is the same as was saved at step 4
- No new nodes were created
