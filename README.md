# [PredictionIO](https://predictionio.incubator.apache.org) recommendation engine for [Heroku](https://www.heroku.com)

🚧 **Work in progress / Alpha / Experimental** 🚧

A machine learning search engine deployable to Heroku with the [PredictionIO buildpack](https://github.com/heroku/predictionio-buildpack).

> The Universal Recommender (UR) is a new type of collaborative filtering recommender based on an algorithm that can use data from a wide variety of user taste indicators&mdash;it is called the [Correlated Cross-Occurrence algorithm](https://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html). Unlike matrix factorization embodied in things like MLlib's ALS, CCO is able to ingest any number of user actions, events, profile data, and contextual information. It then serves results in a fast and scalable way. It also supports item properties for filtering and boosting recommendations and can therefor be considered a hybrid collaborative filtering and content-based recommender.
>
> The use of multiple **types** of data fundamentally changes the way a recommender is used and, when employed correctly, will provide a significant increase in quality of recommendations vs. using only one user event. Most recommenders, for instance, can only use "purchase" events. Using all we know about a user and their context allows us to much better predict their preferences.

—[upstream Github docs](https://github.com/actionml/universal-recommender)


## Local Development

We'll use the 🌱 [`bin/local/` features of predictionio-buildpack](https://github.com/heroku/predictionio-buildpack/tree/local-dev/bin/local) to work locally with this engine.

### 1. PostgreSQL database 

*(This step is only required once for your computer.)*

1. [Install](https://www.postgresql.org/download/) and run PostgreSQL 9.6.
   * for macOS, we 💜 [Postgres.app](http://postgresapp.com)
1. Create the database and grant access to work with the [buildpack's `pio-env.sh` config](https://github.com/heroku/predictionio-buildpack/blob/local-dev/config/pio-env.sh) (database `pio`, username `pio`, & password `pio`):

   ```bash
   $ psql
   =# CREATE DATABASE pio;
   =# CREATE ROLE pio WITH password 'pio';
   =# GRANT ALL PRIVILEGES ON DATABASE pio TO pio;
   =# ALTER ROLE pio WITH LOGIN;
   ```

### 2. PredictionIO runtime

*(This step is only required once for your computer.)*

```bash
# First, change directory up to your top-level projects.
cd ~/my/projects/

git clone https://github.com/heroku/predictionio-buildpack
cd predictionio-buildpack/

# Switch to the feature branch with local dev capabilities (until merged)
git checkout local-dev

export PIO_BUILDPACK_DIR="$(pwd)"
```

As time passes, you may wish to `git pull` the newest buildpack updates.

### 3. The Engine

```bash
# First, change directory up to your top-level projects:
cd ~/my/projects/

git clone https://github.com/heroku/predictionio-engine-ur
cd predictionio-engine-ur/

# Install the environment template; edit it if you need to:
cp .env.local .env

# Setup this working directory:
$PIO_BUILDPACK_DIR/bin/local/setup

# …and initialize the environment:
source $PIO_BUILDPACK_DIR/bin/local/env
```

#### Refreshing the environment

* rerun `bin/local/setup` whenever an env var is changed that effects dependencies, like `PIO_S3_*` or `PIO_ELASTICSEARCH_*` variables
* rerun `bin/local/env` whenever starting in a new terminal or an env var is changed

### 4. Elasticsearch

In a new terminal,

```bash
cd predictionio-engine-ur/PredictionIO-dist/elasticsearch
bin/elasticsearch
```

### 5. Eventserver (optional)

In a new terminal,

```bash
cd predictionio-engine-ur/
source $PIO_BUILDPACK_DIR/bin/local/env
pio eventserver
```

### 6. Finally, use `pio`

```bash
pio status
pio app new ur
pio build --verbose
# Importing data is required before training will succeed
pio train -- --driver-memory 8G
pio deploy
```
