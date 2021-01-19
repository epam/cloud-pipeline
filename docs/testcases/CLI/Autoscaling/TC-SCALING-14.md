# Run more than maximum possible pipelines

**Actions**:

1. Check the System Preference `cluster.max.size` value
2. Launch a pipeline `<count> + 1` times. Where `<count>` is the value saved at step 1

***

**Expected result**:

- There are `<count>` Cloud instances booted
- There are `<count>` nodes in the cluster
- `<count> + 1` pipeline waits for the first node that will be free - to launch
