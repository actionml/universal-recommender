# [PredictionIO](https://predictionio.incubator.apache.org) recommendation engine for [Heroku](https://www.heroku.com)

🚧 **Work in progress / Alpha / Experimental** 🚧

A machine learning search engine deployable to Heroku with the [PredictionIO buildpack](https://github.com/heroku/predictionio-buildpack).

> The Universal Recommender (UR) is a new type of collaborative filtering recommender based on an algorithm that can use data from a wide variety of user taste indicators&mdash;it is called the [Correlated Cross-Occurrence algorithm](https://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html). Unlike matrix factorization embodied in things like MLlib's ALS, CCO is able to ingest any number of user actions, events, profile data, and contextual information. It then serves results in a fast and scalable way. It also supports item properties for filtering and boosting recommendations and can therefor be considered a hybrid collaborative filtering and content-based recommender.
>
> The use of multiple **types** of data fundamentally changes the way a recommender is used and, when employed correctly, will provide a significant increase in quality of recommendations vs. using only one user event. Most recommenders, for instance, can only use "purchase" events. Using all we know about a user and their context allows us to much better predict their preferences.

—[upstream Github docs](https://github.com/actionml/universal-recommender)


## Requirements

* this Heroku-optimized fork of the [Universal Recommender](https://github.com/actionml/universal-recommender) 0.5.0
* [PredictionIO 0.11.0 with support for authenticated Elasticsearch](https://github.com/mars/incubator-predictionio/tree/esclient-auth) ([compare to 0.11.0-incubating release](https://github.com/apache/incubator-predictionio/compare/release/0.11.0...mars:esclient-auth)) (**0.11.0-SNAPSHOT** distribution included with buildpack)
* [Bonsai Add-on](https://elements.heroku.com/addons/bonsai) to provide Elasticsearch 5.x


## Local Development

Use the buildpack setup this engine for **[local development](https://github.com/heroku/predictionio-buildpack/blob/master/DEV.md) including Elasticsearch**.


## Deployment

Adaptation of the normal [PIO engine deployment](https://github.com/heroku/predictionio-buildpack/blob/master/CUSTOM.md#engine).

```bash
# In a clone of this repo
heroku create $APP_NAME

heroku buildpacks:add https://github.com/heroku/heroku-buildpack-jvm-common.git
heroku buildpacks:add https://github.com/heroku/predictionio-buildpack.git

heroku config:set \
  PIO_EVENTSERVER_APP_NAME=ur \
  PIO_EVENTSERVER_ACCESS_KEY=$RANDOM-$RANDOM-$RANDOM-$RANDOM-$RANDOM-$RANDOM \
  PIO_EVENTSERVER_HOSTNAME=my-eventserver.herokuapp.com

heroku addons:create bonsai:shared-10 --as PIO_ELASTICSEARCH --version 5.1
# Verify that Elasticsearch is really version `5.1.x`.

heroku addons:create heroku-postgresql:standard-0
# Wait for Postgres to provision.

git push heroku master

heroku ps:scale web=1:Performance-M release=0:Performance-L train=0:Performance-L
```
