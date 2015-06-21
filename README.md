# Multimodal Recommendation (MMR) Template

## Documentation

Please refer to http://docs.prediction.io/templates/recommendation/quickstart/
Other documentation of the algorithm is [here](http://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html)

The Multimodal Reccomender is a Cooccurrence type that creates indicators from user actions (events) and performs the
recommend query with a Search Engine.


##Configuration, and Queries

The Multimodal Recommender (MMR) will accept a range of data, auto correlate it, and allow for very flexible queries. It is implemented as a PredictionIO Engine. The MMR is different from most recommenders in these ways:
It takes a single very strong event type--one that clearly reflects a user preference--and correlated any number of other event types so much of a user’s clickstream can be used to make recommendations.
It can bias and filter  based on events or item metadata. This means it can give personalized recs that are biased toward “SciFi” and filtered to only include “Premium” items.
It can calculate similar items based on metadata and recommend even if no usage data is known for a user.

###Engine.json

This file allows the user to describe and set parameters that control the engine operations. Some of the parameters work as defaults values for every query and can be overriden in an individual query or added to in the query.

    {
      "id": "default",
      "description": "Default settings",
      "engineFactory": "org.template.RecommendationEngine",
      "datasource": {
        "params" : {
          "name": "sample-movielens",
          "appName": "MMRApp1",
          "eventNames": ["rate", "buy"]
        }
      },
      {“comment”: “This is for Mahout, the values are minimums and should not be removed”},
      "sparkConf": {
        "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
        "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
        "spark.kryo.referenceTracking": "false",
        "spark.kryoserializer.buffer.mb": "200",
        "spark.executor.memory": "4g"
      },
      "algorithms": [
        {
          “comment”: “Here is where all MMR params go”,
          "name": "mmr",
          "params": {
            "appName": "MMRApp1",
            "indexName": "mmrindex",
            "typeName": "items",
            "blacklist": ["buy"],
            “comment”: “these must be ‘hot’,‘trending’, or ‘popular’”
            "backfill": ["trending", "popular"],
            "maxQueryActions": 20,
            "maxRecs": 20,
            "seed": 3,
            “comment”: “and array of fields to be used as biasing factors in all queries”
            “fields”: [
              {
                “name”: ”fieldname”
                “values”: [“fieldValue1”, ...],
                “comment”: “bias negative = filter, positive = amount to boost this in the query”
                “bias”: -maxFloat..maxFloat
              },...
            ]
          }
        }
      ]
    }

The “params” section controls most of the features of the MMR. Possible values are:

* **appName**: string describing the app using the engine.
* **indexName**: string describing the index for all indicators, something like "mmrindex".
* **typeName**: string describing the type in Elasticsearch terminology, something like "items".
* **eventNames**: and array of string identifiers describing action events recorded for users, things like “purchase”, “watch”, “add-to-cart”, even “location”. or “device” can be considered actions and used in recommendations. The first action is to be considered primary, the others secondary for cooccurrence and cross-cooccurrence calculations. 
* **maxQueryActions**: an integer specifying the number of most recent primary actions used to make recommendations for an individual. More implies some will be less recent actions. Theoretically using the right number will capture the user’s current interests.
* **maxRecs**: an integer telling the engine the maximum number of recs to return per query.
* **blacklist**: array of strings corresponding to the actions taken on items, which would cause them to be removed from recs. These will have the same values as some user actions - so “purchase” might be best for an ecom application since there is little need to recommend something the user has already bought. If this is not specified then no blacklist is assumed but one may be passed in with the query.
* **backfill**: array of string corresponding to the types of backfill available. These values are calculated from hot, popular, or trending items and are mixed into the query so they don’t occur unless the other query data produces no results. For example if there is no user history or similar items, only backfill will be returned. 
* **fields**: array of default field based query boosts and filters applied to every query. The name = type or field name for metadata stored in the EventStore with $set and $unset events. Values = and array on one or more values to use in any query. The values will be looked for in the field name. Bias will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name.

###Queries

Query fields determine what data is used to match when returning recs. Some fields have default values in engine.json and so may never be needed in individual queries. On the other hand all values from engine.json may be overridden or added to in an individual query.

    {
      “userId”: “1”,
      “entityId”: “Bra-series-53454543513”,    
      “fields”: [
        {
          “name”: ”fieldname”
          “values”: [“fieldValue1”, ...],// values in the field query
          “bias”: -maxFloat..maxFloat }// negative means a filter, positive is a boost 
        },...
      ]
      “num”: 4,// this number is optional overrides the default in engine.json maxRecs
      “blacklist”: [“itemId1”, “itemId2”, ...]// overrides the blacklist in engine.json and is optional
      “currentTime”: <current_time >, // ISO8601 "2015-01-03T00:12:34.000Z"
    }

* **userId** is the unique id for the user, it may be an anonymous user as long as the id is unique from any authenticated use. This tells the recommender to return recs based on the user’s event history. Used for personalized recommendations.
* **entityId** is the unique item identifier. This tells the recommender to return items similar to this entity. Use for “people who liked this also liked these”.
* **fields**: array of fields values and biases to use in this query. The name = type or field name for metadata stored in the EventStore with $set and $unset events. Values = an array on one or more values to use in this query. The values will be looked for in the field name. Bias will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name.
num max number of recs to return. There is no guarantee that this number will be returned for every query. Adding backfill in the engine.json will make it much more likely to return this number of recs.
* **blacklist** Unlike the engine.json, which specifies event types this part of the query specifies individual items to remove from returned recs. It can be used to remove duplicates when items are already shown in a specific context. This is called anti-flood in recommender use.
 
The query returns personalized recommendations, similar items, or a mix including backfill. The query itself determines this by supplying itemId, userId or both. The boosts and filters are determined by the sign and magnitude of the various metadata “bias” values. Some examples are:

###Simple Non-contextual Personalized

{
  “userId”: “1”,
}

This gets all default values from the engine.json and uses only action indicators for the types specified there.

###Simple Non-contextual Similar Items

{
  “entityId”: “Bra-series-53454543513”,    
}

This returns items that are similar to the query item, and blacklist and backfill are defaulted to what is in the engine.json

###Contextual Personalized

{
  “userId”: “1”,
  “fields”: [
    {
      “name”: “category”
      “values”: [“series”, “mini-series”],
      “bias”: -1 }// filter out all except ‘series’ or ‘mini-series’
    },{
      “name”: “genre”,
      “values”: [“sci-fi”, “detective”]
      “bias”: 10 // boost recs with the `genre’ = `sci-fi` or ‘detective’ by 10
    }
  ]
}

This returns items based on user #1 history filtered by category and boosted to favor more genre specific items. The values for fields have been attached to items with $set events where the “name” corresponds to a doc field and the “values” correspond to the contents of the field. The “bias” is used to indicate a filter or a boost. For Solr or Elasticsearch the boost is sent as-is to the engine and it’s meaning is determined by the engine (Lucene in either case). As always the blacklist and backfill use the defaults in engine.json.


## Versions WIP

### Work in progress, runs on sample data, use at your own risk

  - can be used to recommend for users or items (similar items)
  - boilerpate for bias (boost and filter) based on any indicator for user or item-based.
  - boilerplate for metadata boost and filter
  - integrated with Elasticsearch native (and therefore fast?) Spark based parallel indexing.
  - Runnable on example data for ALS
  - Serving working, query work with item and user, no bias or metadata implemented
  - MMRAlgorithm.predict working with ES multi-indicator query for cooccurrence and cross-cooccurrence
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

  - index droped then written, need to create, then swap for 0 down-time.
  - Only doing usage events now, content similarity is not implemented
  - Context is not allowed in queries yet (location, time of day, device, etc) - bias is speced in engin.json
  - No popularity based fallback yet. - use the EventStore plugin to modify a field in ES docs

