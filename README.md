# Universal Recommender Documentaion

This README is a very small subset of docs at [actionml.com/docs/ur](actionml.com/docs/ur)

All docs are now in a separate repo at [https://github.com/actionml/docs.actionml.com](https://github.com/actionml/docs.actionml.com). If you wish to change or edit those docs make a PR to that repo.

#The Universal Recommender

The Universal Recommender (UR) is a new type of collaborative filtering recommender based on an algorithm that can use data from a wide variety of user taste indicators&mdash;it is called the Correlated Cross-Occurrence algorithm. Unlike  matrix factorization embodied in things like MLlib's ALS, CCO is able to ingest any number of user actions, events, profile data, and contextual information. It then serves results in a fast and scalable way. It also supports item properties for filtering and boosting recommendations and can therefor be considered a hybrid collaborative filtering and content-based recommender.

The use of multiple **types** of data fundamentally changes the way a recommender is used and, when employed correctly, will provide a significant increase in quality of recommendations vs. using only one user event. Most recommenders, for instance, can only use "purchase" events. Using all we know about a user and their context allows us to much better predict their preferences.

#Documentation

 - [The Universal Recommender](http://actionml.com/docs/ur)
 - [The Correlated Cross-Occurrence Algorithm](http://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html)
 - [The Universal Recommender Slide Deck](http://www.slideshare.net/pferrel/unified-recommender-39986309)


All docs for the Universal Recommender are [here](http://actionml.com/docs/ur) and are now hosted in a separate repo at [https://github.com/actionml/docs.actionml.com](https://github.com/actionml/docs.actionml.com). If you wish to change or edit those docs make a PR to that repo.


# Version Changelog

## v0.4.0

 - This version requires PredictionIO-0.9.7-aml found [here](http://actionml/docs/install).
 - New tuning params are now available for each "indicator" type, making indicators with a small number of possible values much more useful&mdash;things like gender or category-preference. See docs for [configuring the UR](http://actionml.com/docs/ur_config) and look for the `indicators` parameter.
 - New forms of recommendations backfill allow all items to be recommended even if they have no user events yet. Backfill types include random and user defined. See docs for [configuring the UR](http://actionml.com/docs/ur_config) and look for the `rankings` parameter.

## v0.3.0

 - This version require PredictionIO-0.9.7-aml from the ActionML repo [here](http://actionml/docs/install).
 - Now supports the `SelfCleanedDataSource` trait. Adding params to the `DataSource` part of `engine.json` allows control of de-duplication, property event compaction, and a time window of event. The time window is used to age out the oldest events. Note: this only works with the ActionML fork of PredictionIO found in the repo mentioned above.
 - changed `backfillField: duration` to accept Scala Duration strings. This will require changes to all engine.json files that were using the older # of seconds duration.
 - added support for indicator predictiveness testing with the MAP@k tool
 - fixed a bug which requires that in the engine.json the `typeName` is required to be `"items"`, with this release the type can be more descriptive.

## v0.2.3

 - removed isEmpty calls that were taking an extremely long time to execute, results in considerable speedup. Now the vast majority of `pio train` time is taken up by writing to Elasticsearch. This can be optimized by creating and ES cluster or giving ES lots of memory.
 
## v0.2.2

 - a query with no item or user will get recommendations based on popularity
 - a new integration test has been added
 - a regression bug where some ids were being tokenized by Elasticsearch, leading to incorrect results, was fixed. **NOTE: for users with complex ids containing dashes or spaces this is an important fix.**
 - a dateRange in the query now takes precidence to the item attached expiration and avaiable dates. 

## v0.2.1

 - date ranges attached to items will be compared to the prediction servers current data if no date is provided in the query. 

## v0.2.0

 - date range filters implemented
 - hot/trending/popular used for backfill and when no other recommendations are returned by the query
 - filters/bias < 0 caused scores to be altered in v0.1.1 fixed in this version so filters have no effect on scoring.
 - the model is now hot-swapped in Elasticsearch so no downtime should be seen, in fact there is no need to run `pio deploy` to make the new model active.
 - it is now possible to have an engine.json (call it something else) dedicated to recalculating the popularity model. This allows fast updates to poularity without recalculating the collaborative filtering model.
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
 
 
#License
This Software is licensed under the Apache Software Foundation version 2 licence found here: http://www.apache.org/licenses/LICENSE-2.0
