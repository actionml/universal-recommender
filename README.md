# Multimodal Recommendation (MMR) Template

## Documentation

Please refer to http://docs.prediction.io/templates/recommendation/quickstart/

The Multimodal Reccomender is a Cooccurrence type that creates indicators from user actions (events) and performs the
recommend query with a Search Engine. 

## Versions

### work in progress, by no means is this runnable

  - Serving and query stubbed
  - early debuggable version of a writer for indicators to ES, still working out how to identify the index, type, doc IDs, and fields
  - MMRModel created but crash before code gets to save.
  - upgraded to PredictionIO 0.9.3
  - MMRAlgorithm.predict stubbed
  - MMRAlgorithm.train working
  - added Mahout's Spark requirements to engine.json sparkConf section, verified that Kryo serialization is working for Mahout objects
  - Lots of work to make debuggable but this is in Intellij land, and only has an effect on build.sbt (as well as PIO on IntelliJ docs)
  - Preparator working
  - DataStore working
  - initial commit
  - clone of ALS template as a base

