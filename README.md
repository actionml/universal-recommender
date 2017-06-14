# [PredictionIO](https://predictionio.incubator.apache.org) recommendation engine for [Heroku](https://www.heroku.com)

A fork of the **[Universal Recommender](https://github.com/actionml/universal-recommender) version 0.5.0** deployable with the [PredictionIO buildpack for Heroku](https://github.com/heroku/predictionio-buildpack).

> The Universal Recommender (UR) is a new type of collaborative filtering recommender based on an algorithm that can use data from a wide variety of user taste indicators&mdash;it is called the [Correlated Cross-Occurrence algorithm](https://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html). …CCO is able to ingest any number of user actions, events, profile data, and contextual information. It then serves results in a fast and scalable way. It also supports item properties for filtering and boosting recommendations and can therefor be considered a hybrid collaborative filtering and content-based recommender.

—[upstream docs](https://github.com/actionml/universal-recommender)

The Heroku app depends on:

* [PredictionIO 0.11.0 with support for authenticated Elasticsearch](https://github.com/mars/incubator-predictionio/tree/esclient-auth) ([compare to 0.11.0-incubating release](https://github.com/apache/incubator-predictionio/compare/release/0.11.0...mars:esclient-auth)). This capability is provided by a **0.11.0-SNAPSHOT** distribution that is included with buildpack for local development and deployment.
* [Bonsai Add-on](https://elements.heroku.com/addons/bonsai) to provide Elasticsearch 5.x

## Demo Story 🐸

This engine demonstrates recommendation of **items** for a **mobile phone user** based on their **purchase history**. The model is trained with a small [example data set](data/initial-events.json).

The **users** and **items** are tagged with platform (`ios` and/or `android`) and the ID's partitioned logically to make it easier to interpret results:
  * `0xx` => `ios` & `android`
  * `1xx` => `android`-only
  * `2xx` => `ios`-only

## How To 📚

✏️ Throughout this document, code terms that start with `$` represent a value (shell variable) that should be replaced with a customized value, e.g `$ENGINE_NAME`…

1. [Requirements](#user-content-requirements)
1. [Deployment](#user-content-deployment)
1. [Configuration](#user-content-configuration)
1. [Local development](#user-content-local-development)

## Requirements

* [Heroku account](https://signup.heroku.com)
* [Heroku CLI](https://toolbelt.heroku.com), command-line tools
* [git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)

## Deployment

Adaptation of the normal [PIO engine deployment](https://github.com/heroku/predictionio-buildpack/blob/master/CUSTOM.md#user-content-engine).

```bash
# In a clone of this repo
heroku create $ENGINE_NAME

heroku buildpacks:add https://github.com/heroku/predictionio-buildpack.git

heroku config:set \
  PIO_EVENTSERVER_APP_NAME=ur \
  PIO_EVENTSERVER_ACCESS_KEY=$RANDOM-$RANDOM-$RANDOM-$RANDOM-$RANDOM-$RANDOM

heroku addons:create bonsai --as PIO_ELASTICSEARCH --version 5.1
# Verify that Elasticsearch is really version `5.x`.
# Some regions provide newer versions, like `--version 5.3`.

heroku addons:create heroku-postgresql:hobby-dev
# Use a higher-level, paid plan for anything but a small demo.

git push heroku master

heroku ps:scale web=1:Standard-2x release=0:Performance-L train=0:Performance-L
```

The sample data in `data/initial-events.json` is imported automatically when deployed. Delete this file if you wish not to have it imported. Note that the engine requires data for training before a deployment will succeed.


## Configuration

* `PIO_UR_ELASTICSEARCH_CONCURRENCY`
  * defaults to `1`
  * may increase in-line with the [Bonsai Add-on plan's](https://elements.heroku.com/addons/bonsai) value for **Concurrent Indexing**
  * the max for a dedicated Elasticsearch cluster is "unlimited", but in reality set it to match the number of Spark executor cores


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