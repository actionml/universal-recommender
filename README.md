# Testing branch

This branch is for testing a pre-release of the UR integrated with Scala 2.11, Spark 2.2.1, Elasticsearch 5.x and a Dataframes version of the model calculation code.

You must build PredictionIO with the default parameters so just run `./make-distribution` this will require you to install Scala 2.11, Spark 2.1.1 or greater, ES 5.5.2 or greater, Hadoop 2.6 or greater. If you have issues getting pio to build and run send questions to the PIO mailing list. Once PIO is running test with `pio status` and `pio app list`. You will need to create an app in import your data to run the integration test to get some sample data installed in the “handmade” app.

*Backup your data*, moving from ES 1 to ES 5 will delete all data!!!! Actually even worse it is still in HBase but you can’t get at it so to upgrade so the following:

 - `pio export` with pio < 0.12.0 =====*Before upgrade!*=====
 - `pio data-delete` all your old apps =====*Before upgrade!*=====
 - build and install pio 0.12.0 including all the services =====*The point of no return!*=====
 - `pio app new …` and `pio import…` any needed datasets
 - download and build Mahout for Scala 2.11 from this repo: https://github.com/actionml/mahout.git follow the instructions in the README.md
 - download the UR from here: https://github.com/actionml/universal-recommender.git and checkout branch 0.7.0-SNAPSHOT
 - replace the line: `resolvers += "Local Repository" at "file:///Users/pat/.custom-scala-m2/repo”` with your path to the local mahout build
 - build the UR with `pio build` or run the integration test to get sample data put into PIO `./examples/integration-test`

This will use the released PIO and alpha UR

a sample of pio-env.sh that works with one type of setup is below, but you'll have to change paths to match yours:


```
#!/usr/bin/env bash

# SPARK_HOME: Apache Spark is a hard dependency and must be configured.
# using Spark 2.2.1 here
SPARK_HOME=/usr/local/spark

# ES_CONF_DIR: You must configure this if you have advanced configuration for
# using ES 5.6.3
ES_CONF_DIR=/usr/local/elasticsearch/config

# HADOOP_CONF_DIR: You must configure this if you intend to run PredictionIO
# using hadoop 2.8 here
HADOOP_CONF_DIR=/usr/local/hadoop/etc/hadoop

# HBASE_CONF_DIR: You must configure this if you intend to run PredictionIO
# using HBase 2.x here
HBASE_CONF_DIR=/usr/local/hbase/conf

# Filesystem paths where PredictionIO uses as block storage.
PIO_FS_BASEDIR=$HOME/.pio_store
PIO_FS_ENGINESDIR=$PIO_FS_BASEDIR/engines
PIO_FS_TMPDIR=$PIO_FS_BASEDIR/tmp

# Storage Repositories
PIO_STORAGE_REPOSITORIES_METADATA_NAME=pio_meta
PIO_STORAGE_REPOSITORIES_METADATA_SOURCE=ELASTICSEARCH

PIO_STORAGE_REPOSITORIES_MODELDATA_NAME=pio_
PIO_STORAGE_REPOSITORIES_MODELDATA_SOURCE=LOCALFS

PIO_STORAGE_REPOSITORIES_APPDATA_NAME=pio_appdata
PIO_STORAGE_REPOSITORIES_APPDATA_SOURCE=ELASTICSEARCH

PIO_STORAGE_REPOSITORIES_EVENTDATA_NAME=pio_eventdata
PIO_STORAGE_REPOSITORIES_EVENTDATA_SOURCE=HBASE

# ES config
PIO_STORAGE_SOURCES_ELASTICSEARCH_TYPE=elasticsearch
PIO_STORAGE_SOURCES_ELASTICSEARCH_HOSTS=localhost
PIO_STORAGE_SOURCES_ELASTICSEARCH_PORTS=9200 # <===== notice 9200 now
PIO_STORAGE_SOURCES_ELASTICSEARCH_CLUSTERNAME=elasticsearch_xyz <===== should match what you have in you ES config file
PIO_STORAGE_SOURCES_ELASTICSEARCH_HOME=/usr/local/elasticsearch

PIO_STORAGE_SOURCES_LOCALFS_TYPE=localfs
PIO_STORAGE_SOURCES_LOCALFS_HOSTS=$PIO_FS_BASEDIR/models

PIO_STORAGE_SOURCES_HBASE_TYPE=hbase
PIO_STORAGE_SOURCES_HBASE_HOME=/usr/local/hbase
```


========================================================================================



# The Universal Recommender

The Universal Recommender (UR) is a new type of collaborative filtering recommender based on an algorithm that can use data from a wide variety of user preference indicators&mdash;it is called the Correlated Cross-Occurrence algorithm. Unlike  matrix factorization embodied in things like MLlib's ALS, CCO is able to ingest any number of user actions, events, profile data, and contextual information. It then serves results in a fast and scalable way. It also supports item properties for building flexible business rules for filtering and boosting recommendations and can therefor be considered a hybrid collaborative filtering and content-based recommender.

Most recommenders can only use conversion events, like buy or rate. Using all we know about a user and their context allows us to much better predict their preferences.

## Experimental Branch with:

 - Spark DataFrame Based Model creation, which is faster than Mahout's math
 - Integration of Elasticsearch 5.x, which is faster and give better results than <= ES 1.7.5

## Raw Dataframes vs Mahout benefits include:

- Significant speed-up of training on large, sparse data sets. The speed-up for a 3M user / 100k item dataset with 500M events is 20x (tested on different cluster configurations).
- Improved scalability, 100% resource utilization during training

## Elasticsearch 5.x support beenfits include:

 - Faster queries
 - Faster indexing
 - Fully uses the REST client and so supports Authentication

## Differences from the Mahout version of the Universal Recommender

The Spark DataFrame based algorithm has the same features and configuration parameters as the main branch of the UR, except the downsampling of events (```maxItemsPerUser```). Downsampling assisted by statistical methods as implemented in Mahout is not (yet) implemented in this version of the UR. Before release as v0.8.0 this will be added.

If no events are downsampled, recommendation differences between the main branch UR and this version should be negligible for 99% of the queries. Small difference may happen due to:
- Inclusion of indicators with zero LLR score. Mahout implementation removes these indicators by default. DataFrame algorithm only removes them if you set the minLLR configuration parameter to a value greater than zero.
- Rank differences of indicators with the same LLR score.

## Examples and tests

You can run `examples/integration-test` as usual to do a basic performance test.

This branch also provides a script (`examples/integration-test-large`) to test the performance on a large random datasets.

See below the training time on a 2014 MacBook Pro for a large randomly generated dataset.

| Dataset | Purchase events | View events | Users* | Items* | Train time UR | Train time UR-DF | Speed up |
|---|---:|---:|---:|---:|---:|---:|---:|
| large | 6M | 8.4M | 850k | 108k | 5083s | 720s | 7x |

*With `minEventsPerUser` set to 3.

To reproduce these results `sh examples/integration-test-large`. It requires Python package Numpy.

# Documentation

 - [The Universal Recommender](http://actionml.com/docs/ur)
 - [The Correlated Cross-Occurrence Algorithm](http://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html)
 - [The Universal Recommender Slide Deck](http://www.slideshare.net/pferrel/unified-recommender-39986309)
 - [Spark DataFrames](https://spark.apache.org/docs/1.6.0/sql-programming-guide.html)


All docs for the Universal Recommender are [here](http://actionml.com/docs/ur) and are hosted at [https://github.com/actionml/docs.actionml.com](https://github.com/actionml/docs.actionml.com). If you wish to change or edit the docs make a PR to that repo.

# Contributions

Contributions are encouraged and appreciated. Create a PR against the [`develop`](https://github.com/actionml/universal-recommender/tree/develop) branch of the git repo. We like to keep new features general so users will not be required to change the code of the UR to make use of the new feature. We will be happy to provide guidance or help via the github PR review mechanism.

# The Universal Recommender Version Log

## Roadmap

PIO-0.11.0 supports use of Elasticsearch 5.x, Spark 2.x, and Scala 2.11. The UR can be compiled for all these except ES 5.x by changing the `build.sbt` file. In a minor release we will provide alternative `build.sbt` files as examples of how to do this. ES 5 support is nearly ready in a PR and will be incorporated as soon as it is ready. ES 5 uses the REST API exclusively and so will support authentication and certain ES as a service hosts. It also has some significant performance improvements.

## v0.6.0

This is a major upgrade release with several new features. Backward compatibility with 0.5.0 is maintained. **Note**: We no longer have a default `engine.json` file so you will need to copy `engine.json.template` to `engine.json` and edit it to fit your data. See the [Universal Recommender Configuration](http://actionml.com/docs/ur_config) docs.

 - **Performance**: Nearly a 40% speedup for most model calculation, and a new tuning parameter that can yield further speed improvements by filtering out unused or less useful data from model building. See `minEventsPerUser` in the UR configuration docs.
 - **Complimentary Purchase aka Item-set Recommendations**: "Shopping-cart" type recommendations. Can be used for wishlists, favorites, watchlists, any list based recommendations. Used with list or user data.
 - **Exclusion Rules**: now we have business rules for inclusion, exclusion, and boosts based on item properties.
 - **PredictionIO 0.11.0**: Full compatibility, but no support for Elasticsearch 5, an option with PIO-0.11.0.
 - **New Advanced Tuning**: Allows several new per indicator / event type tuning parameters for tuning model quality in a more targeted way.
 - **Norms Support**: For large dense datasets norms are now the default for model indexing and queries. This should result in slight precision gains, so better results.
 - **Mahout 0.13.0 Support**: the UR no longer requires a local build of Mahout.
 - **GPU Support**: via Mahout 0.13.0 the core math of the UR now supports the use of GPUs for acceleration.
 - **Timeout Protection**: Queries for users with very large histories could cause a timeout. We now correctly limit the amount of user history that is used as per documentation, which will all but eliminate timeouts.
 - **Bug Fixes**: The use of `blackListEvents` as defined in `engine.json` was not working for an empty list, which should and now does disable any blacklisting except explicit item blacklists contained in the query.

## v0.5.0

 - **Apache PIO Compatible**: The first UR version compatible with Apache PredictionIO-0.10.0-incubating. All past versions do not work and should be upgraded to this. The ActionML build of PIO is permanently deprecated since it is merged with Apache PIO.

## v0.4.2 **Replaces 0.4.1**

 - **Fixes bug** when a `pio build` failure triggered by the release of Apache PIO. If you have problems building v0.4.0 use this version. It is meant to be used with PredictionIO-0.9.7-aml.
 - **Requires a custom build of Apache Mahout**: instructions on the [doc site](http://actionml.com/docs/ur_quickstart) This is temporary until the next Mahout release, when we will update to 0.4.3 (uses predicitonio-0.9.7-aml) and 0.5.0 (which uses predictionio-0.10.0 from Apache)

## v0.4.0

 - This version requires PredictionIO-0.9.7-aml found [here](http://actionml/docs/install).
 - **New tuning params** are now available for each "indicator" type, making indicators with a small number of possible values much more useful&mdash;things like gender or category-preference. See docs for [configuring the UR](http://actionml.com/docs/ur_config) and look for the `indicators` parameter.
 - **New forms of recommendations backfill** allow all items to be recommended even if they have no user events yet. Backfill types include random and user defined. See docs for [configuring the UR](http://actionml.com/docs/ur_config) and look for the `rankings` parameter.

## v0.3.0

 - This version requires PredictionIO-0.9.7-aml from the ActionML repo [here](http://actionml/docs/install).
 - **Implements a moving time window if events**: Now supports the `SelfCleanedDataSource` trait. Adding params to the `DataSource` part of `engine.json` allows control of de-duplication, property event compaction, and a time window of event. The time window is used to age out the oldest events. Note: this only works with the ActionML fork of PredictionIO found in the repo mentioned above.
 - **Parameter changed**: `backfillField: duration` to accept Scala Duration strings. This will require changes to all engine.json files that were using the older # of seconds duration.
 - **Event-types used in queries**: added support for indicator predictiveness testing with the MAP@k tool. This is so only certain mixes of user events are used at query time.
 - **Bug fix**: which requires that the `typeName` in engine.json is required be `"items"`, with this release the type can be any string.

## v0.2.3

 - removed isEmpty calls that were taking an extremely long time to execute, results in considerable speedup. Now the vast majority of `pio train` time is taken up by writing to Elasticsearch. This can be optimized by creating and ES cluster or giving ES lots of memory.

## v0.2.2

 - a query with no item or user will get recommendations based on popularity
 - a new integration test has been added
 - a regression bug where some ids were being tokenized by Elasticsearch, leading to incorrect results, was fixed. **NOTE: for users with complex ids containing dashes or spaces this is an important fix.**
 - a dateRange in the query now takes precedence to the item attached expiration and available dates.

## v0.2.1

 - date ranges attached to items will be compared to the prediction servers current data if no date is provided in the query.

## v0.2.0

 - date range filters implemented
 - hot/trending/popular used for backfill and when no other recommendations are returned by the query
 - filters/bias < 0 caused scores to be altered in v0.1.1 fixed in this version so filters have no effect on scoring.
 - the model is now hot-swapped in Elasticsearch so no downtime should be seen, in fact there is no need to run `pio deploy` to make the new model active.
 - it is now possible to have an engine.json (call it something else) dedicated to recalculating the popularity model. This allows fast updates to popularity without recalculating the collaborative filtering model.
 - Elasticsearch can now be in cluster mode

## v0.1.1

 - ids are now exact matches, for v0.1.0 the ids had to be lower case and were subject to tokenizing analysis so using that version is not recommended.

## v0.1.0

 - user and item based queries supported
 - multiple usage events supported
 - filters and boosts supported on item properties and on user or item based results.
 - fast writing to Elasticsearch using Spark
 - convention over configuration for queries, defaults make simple/typical queries simple and overrides add greater expressiveness.

# Known issues

 - see the github [issues list](https://github.com/PredictionIO/template-scala-parallel-universal-recommendation/issues)


# License
This Software is licensed under the Apache Software Foundation version 2 license found here: http://www.apache.org/licenses/LICENSE-2.0
