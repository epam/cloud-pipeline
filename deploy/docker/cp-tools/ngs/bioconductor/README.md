# Description

Bioconductor is an open source, open development software project to provide tools for the analysis and comprehension of high-throughput genomic data. It is based primarily on the R programming language.

The Bioconductor project started in 2001 and is overseen by a core team, based primarily at Roswell Park Cancer Institute, and by other members coming from US and international institutions. A Technical Advisory Board of key participants meets monthly to support the Bioconductor mission by developing strategies to ensure long-term technical suitability of core infrastructure, and to identify and enable funding strategies for long-term viability. A Scientific Advisory Board including external experts provides annual guidance and accountability.

Key citations to the project include Huber et al., 2015 Nature Methods 12:115-121 and Gentleman et al., 2004 Genome Biology 5:R80

# Main Project Features

* **The R Project for Statistical Computing.** Using R provides a broad range of advantages to the Bioconductor project, including:
A high-level interpreted language to easily and quickly prototype new computational methods.
A well established system for packaging together software with documentation.
An object-oriented framework for addressing the diversity and complexity of computational biology and bioinformatics problems.
Access to on-line computational biology and bioinformatics data.
Support for rich statistical simulation and modeling activities.
Cutting edge data and model visualization capabilities.
Active development by a dedicated team of researchers with a strong commitment to good documentation and software design.
* **Documentation and reproducible research.** Each Bioconductor package contains one or more vignettes, documents that provide a textual, task-oriented description of the package’s functionality. Vignettes come in several forms. Many are “HowTo”s that demonstrate how a particular task can be accomplished with that package’s software. Others provide a more thorough overview of the package or discuss general issues related to the package.
* **Statistical and graphical methods.** The Bioconductor project provides access to powerful statistical and graphical methods for the analysis of genomic data. Analysis packages address workflows for analysis of oligonucleotide arrays, sequence analysis, flow cytometry. and other high-throughput genomic data. The R package system itself provides implementations for a broad range of state-of-the-art statistical and graphical techniques, including linear and non-linear modeling, cluster analysis, prediction, resampling, survival analysis, and time-series analysis.
* **Annotation.** The Bioconductor project provides software for associating microarray and other genomic data in real time with biological metadata from web databases such as GenBank, Entrez genes and PubMed (annotate package). Functions are also provided for incorporating the results of statistical analysis in HTML reports with links to annotation web resources. Software tools are available for assembling and processing genomic annotation data, from databases such as GenBank, the Gene Ontology Consortium, Entrez genes, UniGene, the UCSC Human Genome Project (AnnotationDbi package). Annotation data packages are distributed to provide mappings between different probe identifiers (e.g. Affy IDs, Entrez genes, PubMed). Customized annotation libraries can also be assembled.

# Configuration

This docker image is built from the [bioconductor/release_core2](https://hub.docker.com/r/bioconductor/release_core2) and exposes RStudio interactive endpoint

# Running RStudio

To run this tool perform the following steps:
1. Click `Run` button in the top-right corner and agree on a tool launch
2. Wait for the container to be initialized (`InitializeEnvironment` task shall be marked as finished)
3. Click `Endpoint` link to navigate to the RStudio Web GUI

# Accessing data from RStudio instance

Once RStudio started and initialized - the following data locations are made available:
1. Buckets (i.e. DataStorage) that are available to user - will be mounted to `~/cloud-data/{BUCKET_NAME}`
2. CloudPipeline CLI is installed and configured automatically - `pipe storage ls/cp/mv` commands can be run to manage data within DataStorages
3. `wget` and `curl` commands can be used as well to get data from the external resources
4. Data can be uploaded using RStudio Web GUI (`Files -> Upload`)
