# Multimodal Recommendation (MMR) Template

## Documentation

Please refer to http://docs.prediction.io/templates/recommendation/quickstart/
Other documentation of the algorithm is [here](http://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html)

The Multimodal Reccomender is a Cooccurrence type that creates indicators from user actions (events) and performs the
recommend query with a Search Engine.

See engine.json for configurable parameters. 

## Versions

### Work in progress, runs on sample data, use at your own risk

  - Runnable on example data for ALS
  - Serving working
  - MMRAlgorithm.predict working with ES multi-indicator query
  - writer for indicators to ES working, still thinking about how to identify the index, type, doc IDs, and fields
  - MMRModel created
  - upgraded to PredictionIO 0.9.3
  - MMRAlgorithm.predict stubbed
  - MMRAlgorithm.train working
  - added Mahout's Spark requirements to engine.json sparkConf section, verified that Kryo serialization is working for Mahout objects
  - Lots of work to make debuggable but this is in Intellij land, and only has an effect on build.sbt (as well as PIO on IntelliJ docs)
  - Preparator working
  - DataStore working
  - initial commit
  - clone of ALS template as a base
  
### Known issues

  - Need to save item ids to ES as an array of strings, not as a space delimited single string as it is done now
  - Find a better way to id the index, it's hard coded to "mmrindex" now so multiple engines would conflict
  - Do we need to manage indexes for performance. For now each field is written to the index and the index is updated every write. For blasting a new model into the index this may affect performance.
  - Not sure how to handle the case where the user is allowed to alter metadata. If we modify in place the model is immortal but refreshed. But if it's immortal how do items get removed permanently since they are derived from items that get interactions.
  - Only doing usage events now, content similarity is not implemented
  - Context is not allowed in queries yet (location, time of day, device, etc)
  - No popularity based fallback yet.

