# Description

The Common Workflow Language (CWL) is an open standard for describing analysis workflows and tools in a way that makes
them portable and scalable across a variety of software and hardware environments, from workstations to cluster, cloud,
and high performance computing (HPC) environments. CWL is designed to meet the needs of data-intensive science, such as
Bioinformatics, Medical Imaging, Astronomy, High Energy Physics, and Machine Learning.

# References

Main website: <https://www.commonwl.org>

CWL v1.0.x: <https://github.com/common-workflow-language/common-workflow-language>

Toil tool: <https://github.com/DataBiosphere/toil>

# Docker information

This docker image is based on `python 3.7` image and contains `toil` tool for running `CWL` pipelines. This image
provides a `cwl-runner` script to easily run CWL pipelines. The input arguments can be auto generated from provided
CWl file and input run parameters. Run example:

```
cwl-runner example.cwl
```
