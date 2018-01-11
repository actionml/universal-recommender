# Spark DataFrame Based Universal Recommender

This branch provides a version of the Universal Recommender that uses Spark DataFrames Join operations to calculate the cross-occurrence indicators. Benefits include:

- Significant speed-up of training on large, sparse data sets. The speed-up for a 3M user / 100k item dataset with 500M events is 20x (tested on different cluster configurations).
- Improved scalability, 100% resource utilization during training
- Easier to customize, algorithm is included in the Universal Recommender template code.

# Differences with main branch Universal Recommender

The Spark DataFrame based algorithm has the same features and configuration parameters as the main branch of the UR, except the downsampling of events (```maxItemsPerUser```). Downsampling assisted by statistical methods as implemented in Mahout is not (yet) implemented in this version of the UR.

If no events are downsampled, recommendation differences between the main branch UR and this version should be negligible for 99% of the queries. Small difference may happen due to:
- Inclusion of indicators with zero LLR score. Mahout implementation removes these indicators by default. DataFrame algorithm only removes them if you set the minLLR configuration parameter to a value greater than zero.
- Rank differences of indicators with the same LLR score.

# How to use

This branch of the UR can be used in exactly the same way as the main branch, except that the parameter ```maxItemsPerUser``` is not used. It is recommended to tune the ```spark.sql.shuffle.partitions``` Spark setting. Spark-Hive is an extra depedency.

# Demo

You can run ```examples/integration-test``` as usual to do a basic performance test.

This branch also provides a script (```examples/integration-test-large```) to test the performance on a large random datasets.

See below the training time on a 2014 MacBook Pro for a large randomly generated dataset.

| Dataset | Purchase events | View events | Users* | Items* | Train time UR | Train time UR-DF | Speed up |
|---|---:|---:|---:|---:|---:|---:|---:|
| large | 6M | 8.4M | 850k | 108k | 5083s | 720s | 7x |

*With ```minEventsPerUser``` set to 3.

To reproduce these results```sh examples/integration-test-large```. It requires Python package Numpy.

# Documentation

 - [The Universal Recommender](http://actionml.com/docs/ur)
 - [Spark DataFrames](https://spark.apache.org/docs/1.6.0/sql-programming-guide.html)

# Background

The DataFrame algorithm was developed by Macy’s PROS Recommendations Team to speed up training of the UR.

# License
This Software is licensed under the Apache Software Foundation version 2 license found here: http://www.apache.org/licenses/LICENSE-2.0
