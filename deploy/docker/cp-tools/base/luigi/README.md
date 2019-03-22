![Jamie, the Cromwell pig](https://raw.githubusercontent.com/spotify/luigi/master/doc/luigi.png)

Luigi is a Python (2.7, 3.6, 3.7 tested) package that helps you build complex
pipelines of batch jobs. It handles dependency resolution, workflow management,
visualization, handling failures, command line integration, and much more.


# Background

The purpose of Luigi is to address all the plumbing typically associated
with long-running batch processes. You want to chain many tasks,
automate them, and failures *will* happen. These tasks can be anything,
but are typically long running things like
[Hadoop](http://hadoop.apache.org/) jobs, dumping data to/from
databases, running machine learning algorithms, or anything else.

There are other software packages that focus on lower level aspects of
data processing, like [Hive](http://hive.apache.org/),
[Pig](http://pig.apache.org/), or
[Cascading](http://www.cascading.org/). Luigi is not a framework to
replace these. Instead it helps you stitch many tasks together, where
each task can be a [Hive query](https://luigi.readthedocs.io/en/latest/api/luigi.contrib.hive.html),
a [Hadoop job in Java](https://luigi.readthedocs.io/en/latest/api/luigi.contrib.hadoop_jar.html),
a  [Spark job in Scala or Python](https://luigi.readthedocs.io/en/latest/api/luigi.contrib.spark.html),
a Python snippet,
[dumping a table](https://luigi.readthedocs.io/en/latest/api/luigi.contrib.sqla.html)
from a database, or anything else. It's easy to build up
long-running pipelines that comprise thousands of tasks and take days or
weeks to complete. Luigi takes care of a lot of the workflow management
so that you can focus on the tasks themselves and their dependencies.

You can build pretty much any task you want, but Luigi also comes with a
*toolbox* of several common task templates that you use. It includes
support for running
[Python mapreduce jobs](https://luigi.readthedocs.io/en/latest/api/luigi.contrib.hadoop.html)
in Hadoop, as well as
[Hive](https://luigi.readthedocs.io/en/latest/api/luigi.contrib.hive.html),
and [Pig](https://luigi.readthedocs.io/en/latest/api/luigi.contrib.pig.html),
jobs. It also comes with
[file system abstractions for HDFS](https://luigi.readthedocs.io/en/latest/api/luigi.contrib.hdfs.html),
and local files that ensures all file system operations are atomic. This
is important because it means your data pipeline will not crash in a
state containing partial data.


# Authors

Luigi was built at [Spotify](https://www.spotify.com), mainly by
[Erik Bernhardsson](https://github.com/erikbern) and
[Elias Freider](https://github.com/freider).
[Many other people](https://github.com/spotify/luigi/graphs/contributors)
have contributed since open sourcing in late 2012.
[Arash Rouhani](https://github.com/tarrasch) is currently the chief
maintainer of Luigi.
