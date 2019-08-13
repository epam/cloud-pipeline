![Nextflow logo](https://raw.githubusercontent.com/nextflow-io/trademark/master/nextflow2014_no-bg.png)


References
=========

https://www.nextflow.io/

https://github.com/nextflow-io/nextflow

https://www.nextflow.io/docs/latest/getstarted.html


Rationale
=========

With the rise of big data, techniques to analyse and run experiments on large datasets are increasingly necessary.

Parallelization and distributed computing are the best ways to tackle this problem, but the tools commonly available to the bioinformatics community often lack good support for these techniques, or provide a model that fits badly with the specific requirements in the bioinformatics domain and, most of the time, require the knowledge of complex tools or low-level APIs.

Nextflow framework is based on the dataflow programming model, which greatly simplifies writing parallel and distributed pipelines without adding unnecessary complexity and letting you concentrate on the flow of data, i.e. the functional logic of the application/algorithm.

It doesn't aim to be another pipeline scripting language yet, but it is built around the idea that the Linux platform is the *lingua franca* of data science, since it provides many simple command line and scripting tools, which by themselves are powerful, but when chained together facilitate complex data manipulations.

In practice, this means that a Nextflow script is defined by composing many different processes. Each process can execute a given bioinformatics tool or scripting language, to which is added the ability to coordinate and synchronize the processes execution by simply specifying their inputs and outputs.
