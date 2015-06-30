# Multimodal Recommendation (MMR) Template

## Documentation

Please refer to http://docs.prediction.io/templates/recommendation/quickstart/
Other documentation of the algorithm is [here](http://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html)

The Multimodal Reccomender is a Cooccurrence type that creates indicators from user actions (events) and performs the
recommend query with a Search Engine.


##Configuration, and Queries

The Multimodal Recommender (MMR) will accept a range of data, auto correlate it, and allow for very flexible queries. It is implemented as a PredictionIO Engine. The MMR is different from most recommenders in these ways:

* It takes a single very strong "primary" event type&mdash;one that clearly reflects a user's preference&mdash;and correlates any number of other event types to the primary event. This has the effect of using virtually any user action to recommend the primary action. Much of a user’s clickstream can be used to make recommendations. If a user has no history of the primary action (purchase for instance) but does have history of views, personalized recommendations for purchases can be made. With user purchase history the recommendations become better. ALS-type recommenders have been used with event weights but except for ratings these seldom actually result in better performance.
* It can boost and filter based on events or item metadata. This means it can give personalized recs that are biased toward “SciFi” and filtered to only include “Promoted” items when the business rule call for this.
* It can use a user's context to make recommendations even when the user is new. If usage data has been gathered for other users referring URL, device type, or location, for instance, there may be a corelation between this data and some items that were prefered. The MMR can detect this when it exists and recommend based on this context, even to new users. We call this "micro-segmented" recommendations since they are not personal but group users based on limited contextual information. These will not be as good as when more is know about the user but may be better than simply returning popular items.
* It includes a fallback to some form of item popularity when there is no other information known about the user.
* All of the above can be mixed into a single query for blended results and so the query can be tuned to a great many applicaitons. Also since only one query is made and boosting is supported, a query can be constructed with several fallbacks. Usage data is most important so boost that high, micro-segemnted data may be useful so boost that lower, and popularity is given no boost for when you have no other data.

###Biases

These take the form of boosts and filters where a neutral bias is 1.0. The importance of some part of the query may be boosted by a positive non-zero float. If the bias is < 0 it is considered a filter&mdash;meaning no recommendation is made that lacks the filter value(s). One example of a filter is where it may make sense to show only "electronics" recommendations when the user is viewing an electroncs product. Biases are often applied to a list of data, for instance the user is looking at a video page with a cast of actors. The "cast" list is metadata attached to items and a query can show "people who liked this, also liked these" type recs but also include the current cast boosted by 0.5. This can be seen as showing similar item recs but using the cast in the query in a way that will not overpower the similar items (since by default they have a neutral 1.0 boost).


###Engine.json

This file allows the user to describe and set parameters that control the engine operations. Some of the parameters work as defaults values for every query and can be overriden or added to in the query.

    {
      "id": "default",
      "description": "Default settings",
      "engineFactory": "org.template.RecommendationEngine",
      "datasource": {
        "params" : {
          "name": "sample-movielens",
          "appName": "MMRApp1",
          "eventNames": ["rate", "buy"] // name your events with any string
        }
      },
      {“comment”: “This is for Mahout and Elasticsearch, the values are minimums and should not be removed”},
      "sparkConf": {
        "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
        "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
        "spark.kryo.referenceTracking": "false",
        "spark.kryoserializer.buffer.mb": "200",
        "spark.executor.memory": "4g",
        "es.index.auto.create": "true"
      },
      "algorithms": [
        {
          "name": "mmr",
          "params": {
            "appName": "MMRApp1",
            "indexName": "mmrindex",
            "typeName": "items",
            "blacklist": ["buy"],
            "backfill": "trending" or "popular" or "trending",
            "maxQueryActions": 20,
            "maxRecs": 20,
            "seed": 3,
            "userbias": -maxFloat..maxFloat, // favor user history in recs by this amount
            "itembias": -maxFloat..maxFloat, //favor similar items in recs by this amount
            "returnSelf": true | false //default = false, will not return item or user as recommendation
            “fields”: [ // an array of fields to be used as biasing factors in all queries
              {
                “name”: ”fieldname”
                “values”: [“fieldValue1”, ...],
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
* **maxRecs**: an integer telling the engine the maximum number of recs to return per query but less may be returned if the query produces less results.
* **blacklist**: array of strings corresponding to the actions taken on items, which would cause them to be removed from recs. These will have the same values as some user actions - so “purchase” might be best for an ecom application since there is little need to recommend something the user has already bought. If this is not specified then no blacklist is assumed but one may be passed in with the query. Note that not all actions are taken on the same items being recommended. For instance every time a user goes to a categpory page this could be recorded as a category preference so if this event is used in a blacklist it should have no effect, the catagory and item ids should never match. 
* **backfill**: array of strings corresponding to the types of backfill available. These values are calculated from hot, popular, or trending items and are mixed into the query so they don’t occur unless the other query data produces no results. For example if there is no user history or similar items, only backfill will be returned. Backfil bias is always 1 so increasing the bias for user or item data is usual when including backfill so their results will dominate. **Note**: not sure if this is best, may want to boost lower than one and allow an override backfillBias since users will seldom want to mix personal and popular recommendations equally.
* **fields**: array of default field based query boosts and filters applied to every query. The name = type or field name for metadata stored in the EventStore with $set and $unset events. Values = and array of one or more values to use in any query. The values will be looked for in the field name. Bias will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name.
* **userBias**: amount to favor user history in creating recs, 1 is neutral, and negative number means to use as a filter so the user history must be used i recs, any positive number greater than one will boost the importance of user history in recs.
* **itemBias**: same as userbias but applied to similar items to the item supplied in the query.
* **returnSelf**: boolean asking to include the item that was part of the query (if there was one) as part of the results. The default is false and this is by far the most common use so this is seldom required.

###Queries

Query fields determine what data is used to match when returning recs. Some fields have default values in engine.json and so may never be needed in individual queries. On the other hand all values from engine.json may be overridden or added to in an individual query. The only requirement is that there must be a user or item in every query.

    {
      “user”: “xyz”, 
      “userBias”: -maxFloat..maxFloat,
      “item”: “53454543513”, 
      “itemBias”: -maxFloat..maxFloat,  
      “num”: 4,// this number is optional overrides the default in engine.json maxRecs
      “fields”: [
        {
          “name”: ”fieldname” // may have several fields in query
          “values”: [“fieldValue1”, ...],// values in the field query
          “bias”: -maxFloat..maxFloat }// negative means a filter, positive is a boost 
        },...
      ]
      “blacklist”: [“itemId1”, “itemId2”, ...]// overrides the blacklist in engine.json and is optional
      "returnSelf": true | false //default = false, will not return query item as recommendation
      “currentTime”: <current_time >, // ISO8601 "2015-01-03T00:12:34.000Z"
    }

* **user** contains a unique id for the user. This may be a user not in the **training** data, so a new or anonymous user who has an anonymous id. All user history captured in near realtime can be used to influence recommendations, there is no need to retrain to enable this.
* **userBias** the amount to favor the user's history in making recs. The user may be anonymous as long as the id is unique from any authenticated user. This tells the recommender to return recs based on the user’s event history. Used for personalized recommendations. Overrides and bias in engine.json
* **item** contains the unique item identifier
* **itemBias** the amount to favor similar items in making recs. This tells the recommender to return items similar to this the item specified. Use for “people who liked this also liked these”. Overrides any bias in engine.json
* **fields**: array of fields values and biases to use in this query. The name = type or field name for metadata stored in the EventStore with $set and $unset events. Values = an array on one or more values to use in this query. The values will be looked for in the field name. Bias will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name.
num max number of recs to return. There is no guarantee that this number will be returned for every query. Adding backfill in the engine.json will make it much more likely to return this number of recs.
* **blacklist** Unlike the engine.json, which specifies event types this part of the query specifies individual items to remove from returned recs. It can be used to remove duplicates when items are already shown in a specific context. This is called anti-flood in recommender use.
* **returnSelf**: boolean asking to include the item that was part of the query (if there was one) as part of the results. Defaults to false.
 
The query returns personalized recommendations, similar items, or a mix including backfill. The query itself determines this by supplying item, user or both. Some examples are:

###Simple Non-contextual Personalized

	{
	  “user”: “xyz”
	}
	
This gets all default values from the engine.json and uses only action indicators for the types specified there.

###Simple Non-contextual Similar Items

	{
	  “item”: “53454543513”   
	}
	
This returns items that are similar to the query item, and blacklist and backfill are defaulted to what is in the engine.json

###Contextual Personalized

	{
	  “user”: “xyz”,
	  “fields”: [
	    {
	      “name”: “categories”
	      “values”: [“series”, “mini-series”],
	      “bias”: -1 }// filter out all except ‘series’ or ‘mini-series’
	    },{
	      “name”: “genre”,
	      “values”: [“sci-fi”, “detective”]
	      “bias”: 1.2 // boost/favor recs with the `genre’ = `sci-fi` or ‘detective’
	    }
	  ]
	}

This returns items based on user "xyz" history filtered by categories and boosted to favor more genre specific items. The values for fields have been attached to items with $set events where the “name” corresponds to a doc field and the “values” correspond to the contents of the field. The “bias” is used to indicate a filter or a boost. For Solr or Elasticsearch the boost is sent as-is to the engine and it’s meaning is determined by the engine (Lucene in either case). As always the blacklist and backfill use the defaults in engine.json.

###Contextual Personalized with Similar Items

	{
	  “user”: “xyz”, 
	  "userBias": 2, // favor personal recs
	  “item”: “53454543513”, // fallback to contextual recs
	  “fields”: [
	    {
	      “name”: “categories”
	      “values”: [“series”, “mini-series”],
	      “bias”: -1 }// filter out all except ‘series’ or ‘mini-series’
	    },{
	      “name”: “genre”,
	      “values”: [“sci-fi”, “detective”]
	      “bias”: 1.2 // boost/favor recs with the `genre’ = `sci-fi` or ‘detective’
	    }
	  ]
	}

This returns items based on user xyz history or similar to item 53454543513 but favoring user hostory recs. These are filtered by categories and boosted to favor more genre specific items. 

**Note**:This query should be condsidered **experimental**. mixing user history with item similairty is possible but may have unexpected results.

## Versions WIP

### Work in progress, runs on sample data, use at your own risk

  - all except backfill working in the happy path lots of testing still needed
  - working for item, user, both with boost but filter not working yet
  - can be used to recommend for users and/or items (similar items)
  - boilerpate for bias (boost and filter) based on any indicator for user or item-based.
  - boilerplate for metadata boost and filter
  - integrated with Elasticsearch native (and therefore fast?) Spark based parallel indexing.
  - Runnable on example data for ALS
  - Serving working, query works with item and user, no bias or metadata implemented
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

  - dates not implemented
  - index droped then written, need to create, then swap for 0 down-time? Not sure 
  - Only doing usage events now, content similarity is not implemented
  - Context is not allowed in queries yet (location, time of day, device, etc) - bias is speced in engin.json
  - No popularity based fallback yet. - use the EventStore plugin to modify a field in ES docs

