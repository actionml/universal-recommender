# [PredictionIO](https://predictionio.incubator.apache.org) recommendation engine for [Heroku](https://www.heroku.com)

🚧 **Work in progress / Alpha / Experimental** 🚧

A machine learning search engine deployable to Heroku with the [PredictionIO buildpack](https://github.com/heroku/predictionio-buildpack).

> The Universal Recommender (UR) is a new type of collaborative filtering recommender based on an algorithm that can use data from a wide variety of user taste indicators&mdash;it is called the [Correlated Cross-Occurrence algorithm](https://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html). Unlike matrix factorization embodied in things like MLlib's ALS, CCO is able to ingest any number of user actions, events, profile data, and contextual information. It then serves results in a fast and scalable way. It also supports item properties for filtering and boosting recommendations and can therefor be considered a hybrid collaborative filtering and content-based recommender.
>
> The use of multiple **types** of data fundamentally changes the way a recommender is used and, when employed correctly, will provide a significant increase in quality of recommendations vs. using only one user event. Most recommenders, for instance, can only use "purchase" events. Using all we know about a user and their context allows us to much better predict their preferences.

—[upstream Github docs](https://github.com/actionml/universal-recommender)


## Local Development

These instructions have been generalized and are evolving in the [`local-dev` feature branch of predictionio-buildpack](https://github.com/heroku/predictionio-buildpack/blob/local-dev/CUSTOM.md#local-development). Please follow along there for local setup & usage.
