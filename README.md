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

1. ⚠️ [Requirements](#user-content-requirements)
1. 🚀 [Demo Deployment](#user-content-demo-deployment)
   1. [Create the app](#user-content-create-the-app)
   1. [Configure the app](#user-content-configure-the-app)
   1. [Provision Elasticsearch](#user-content-provision-elasticsearch)
   1. [Provision Postgres](#user-content-provision-postgres)
   1. [Import data](#user-content-import-data)
   1. [Deploy the app](#user-content-deploy-the-app)
   1. [Scale up](#user-content-scale-up)
   1. [Retry release](#user-content-retry-release)
   1. [Diagnostics](#user-content-diagnostics)
1. 🎯 [Query for predictions](#user-content-query-for-predictions)
1. 🛠 [Local development](#user-content-local-development)
   1. [Import sample data](#user-content-import-sample-data)
   1. [Run `pio`](#user-content-run-pio)
   1. [Query the local engine](#user-content-query-the-local-engine)
1. 🎛 [Configuration options](#user-content-configuration)


## Requirements

* [Heroku account](https://signup.heroku.com)
* [Heroku CLI](https://toolbelt.heroku.com), command-line tools
* [git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)


## Demo Deployment

Adaptation of the normal [PIO engine deployment](https://github.com/heroku/predictionio-buildpack/blob/master/CUSTOM.md#user-content-engine).

### Create the app

```bash
git clone \
  https://github.com/heroku/predictionio-engine-ur.git \
  pio-engine-ur

cd pio-engine-ur

heroku create $ENGINE_NAME
heroku buildpacks:add https://github.com/heroku/predictionio-buildpack.git
```

### Configure the app

```bash
heroku config:set \
  PIO_EVENTSERVER_APP_NAME=ur \
  PIO_EVENTSERVER_ACCESS_KEY=$RANDOM-$RANDOM-$RANDOM-$RANDOM-$RANDOM-$RANDOM \
  PIO_UR_ELASTICSEARCH_CONCURRENCY=1
```

### Provision Elasticsearch

```bash
heroku addons:create bonsai --as PIO_ELASTICSEARCH --version 5.1
```

* Verify that Elasticsearch is really version `5.x`.
* Some regions provide newer versions, like `--version 5.3`.


### Provision Postgres

```bash
heroku addons:create heroku-postgresql:hobby-dev
```

* Use a higher-level, paid plan for anything but a small demo.
* `hobby-basic` is the smallest paid [heroku-postgresql plan](https://elements.heroku.com/addons/heroku-postgresql#pricing)

### Import data

Initial training data is automatically imported from [`data/initial-events.json`](data/initial-events.json).

👓 When you're ready to begin working with your own data, read about strategies for [importing data](https://github.com/heroku/predictionio-buildpack/blob/master/CUSTOM.md#import-data).

### Deploy the app

```bash
git push heroku master

# Follow the logs to see training & web start-up
#
heroku logs -t
```

⚠️ **Initial deploy will probably fail due to memory constraints.** Proceed to scale up.

### Scale up

Once deployed, scale up the processes to avoid memory issues:

```bash
heroku ps:scale \
  web=1:Standard-2X \
  release=0:Performance-L \
  train=0:Performance-L
```

💵 *These are paid, [professional dyno types](https://devcenter.heroku.com/articles/dyno-types#available-dyno-types)*

### Retry release

When the release (`pio train`) fails due to memory constraints or other transient error, you may use the Heroku CLI [releases:retry plugin](https://github.com/heroku/heroku-releases-retry) to rerun the release without pushing a new deployment:

```bash
# First time, install it.
heroku plugins:install heroku-releases-retry

# Re-run the release & watch the logs
heroku releases:retry
heroku logs -t
```

## Query for predictions

Once deployment completes, the engine is ready to recommend of **items** for a **mobile phone user** based on their **purchase history**.

Submit [queries to Universal Recommender](http://actionml.com/docs/ur_queries) to get recommendations.

Get all recommendations for a user:

```bash
# an Android user
curl -X "POST" "http://$ENGINE_NAME.herokuapp.com/queries.json" \
     -H "Content-Type: application/json" \
     -d $'{"user": "100"}'
```

```bash
# an iPhone user
curl -X "POST" "http://$ENGINE_NAME.herokuapp.com/queries.json" \
     -H "Content-Type: application/json" \
     -d $'{"user": "200"}'
```


Get recommendations for a user omitting *phones*:

```bash
curl -X "POST" "http://$ENGINE_NAME.herokuapp.com/queries.json" \
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

Get accessory recommendations for a user omitting *phones* & boosting *power-related items*:

```bash
curl -X "POST" "http://$ENGINE_NAME.herokuapp.com/queries.json" \
     -H "Content-Type: application/json" \
     -d $'{
            "user": "100",
            "fields": [{
              "name": "category",
              "values": ["phone"],
              "bias": 0
            }],
            "fields": [{
              "name": "category",
              "values": ["power"],
              "bias": 0.5
            }]
          }'
```

For a user with no purchase history, the recommendations will be based on popularity:

```bash
curl -X "POST" "http://$ENGINE_NAME.herokuapp.com/queries.json" \
     -H "Content-Type: application/json" \
     -d $'{"user": "000"}'
```


## Local Development

First, use the buildpack to **[setup local development](https://github.com/heroku/predictionio-buildpack/blob/master/DEV.md) including Elasticsearch**.

### Import sample data

```bash
bin/pio app new ur
PIO_EVENTSERVER_APP_NAME=ur data/import-events -f data/initial-events.json
```

### Run `pio`

```bash
bin/pio build
bin/pio train --driver-memory 2500m
bin/pio deploy
```

### Query the local engine

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


## Configuration

* `PIO_UR_ELASTICSEARCH_CONCURRENCY`
  * defaults to `1`
  * may increase in-line with the [Bonsai Add-on plan's](https://elements.heroku.com/addons/bonsai) value for **Concurrent Indexing**
  * the max for a dedicated Elasticsearch cluster is "unlimited", but in reality set it to match the number of Spark executor cores

