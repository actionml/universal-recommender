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

First, use the buildpack to **[setup local development](https://github.com/heroku/predictionio-buildpack/blob/master/DEV.md) including Elasticsearch**.

### Import sample data

```bash
bin/pio app new ur
PIO_EVENTSERVER_APP_NAME=ur data/import-events -f data/initial-events.json
```

### Usage

```bash
bin/pio build
bin/pio train --driver-memory 2500m
bin/pio deploy
```

Example query with the sample data:

```bash
curl -X "POST" "http://127.0.0.1:8000/queries.json" \
     -H "Content-Type: application/json" \
     -d $'{
            "user": "100",
            "fields": [{
              "name": "category",
              "values": ["phone"],
              "bias": 0
            }]
          }'
```

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
# Verify that Elasticsearch is really version `5.x`.
# May provide newer versions too, like `--version 5.3`.

# Use the Eventserver's database with this engine.
# Do not share them between Universal Recommender engines.
heroku addons:attach $EVENTSERVER_DATABASE_ADDON_ID

git push heroku master

heroku ps:scale web=1:Performance-M release=0:Performance-L train=0:Performance-L
```

The sample data in `data/initial-events.json` is imported automatically when deployed. Delete this file if you wish not to have it imported. Note that the engine requires data for training before a deployment will succeed.

## Configuration

* `PIO_UR_ELASTICSEARCH_CONCURRENCY`
  * defaults to `1`
  * may increase in-line with the [Bonsai Add-on plan's](https://elements.heroku.com/addons/bonsai) value for **Concurrent Indexing**
  * the max for a dedicated Elasticsearch cluster is "unlimited", but in reality set it to match the number of Spark executor cores
