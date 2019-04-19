# Pipeline purpose

This Pipeline is used to demonstrate building custom pipelines within a Cloud Pipeline platform

It contains a set of typical NGS tools to perform basic operations `QC -> Align -> Variant Calling`

Refer to `ngs/ngs-essential` for a docker image and toolset decription

*Note: It is not supposed to be used in "real world" scenarios*

# Image toolset

**ngs-essential** image contains the following tools installed
- FastQC 0.11.5
- BWA 0.7.15
- HTSLib 1.7
- samtools 1.7
- Vardict Java 1.5.1
