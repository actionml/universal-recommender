# Universal Recommendation Template

The Universal Recommender (UR) is a Cooccurrence type that creates correlators from several user actions, events, or profile information and performs the recommendations query with a Search Engine. It also supports item properties for filtering and boosting recommendations. This allows users to make use of any part of their user's clickstream or even profile and context information in making recommendations. TBD: several forms of popularity type backfill and content-based correlators for content based recs. Also filters on property date ranges. With these additions it will more closely live up to the name "Universal"

##Quick Start

 1. [Install the PredictionIO framework](https://docs.prediction.io/install/) **be sure to choose HBase and Elasticsearch** for storage. This template requires Elasticsearch.
 2. Make sure the PIO console and services are running, check with `pio status`
 3. [Install this template](https://docs.prediction.io/start/download/) **be sure to specify this template** with `pio template get PredictionIO/template-scala-parallel-universal-recommendation`
 
**To import and experiment with the simple example data**

1. Create a new app name, change `appName` in `engine.json`
2. Run `pio app new **your-new-app-name**`
4. Import sample events by running `python examples/import_handmade.py --access_key **your-access-key**` where the key can be retrieved with `pio app list`
3. The engine.json file in the root directory of your new UR template is set up for the data you just imported (make sure to create a new one for your data) Edit this file and change the `appName` parameter to match what you called the app in step #2
5. Perform `pio build`, `pio train`, and `pio deploy`
6. To execute some sample queries run `./examples/query-handmade.sh`

If there are timeouts, enable the delays that are commented out in the script&mdash;for now. In the production environment the engines will "warm up" with caching and will execute queries much faster. Also all services can be configured or scaled to meet virtually any performance needs.

##What is a Universal Recommender

The Universal Recommender (UR) will accept a range of data, auto correlate it, and allow for very flexible queries. The UR is different from most recommenders in these ways:

* It takes a single very strong "primary" event type&mdash;one that clearly reflects a user's preference&mdash;and correlates any number of other event types to the primary event. This has the effect of using virtually any user action to recommend the primary action. Much of a user’s clickstream can be used to make recommendations. If a user has no history of the primary action (purchase for instance) but does have history of views, personalized recommendations for purchases can still be made. With user purchase history the recommendations become better. ALS-type recommenders have been used with event weights but except for ratings these often do not result in better performance.
* It can boost and filter based on events or item metadata/properties. This means it can give personalized recs that are biased toward “SciFi” and filtered to only include “Promoted” items when the business rules call for this.
* It can use a user's context to make recommendations even when the user is new. If usage data has been gathered for other users for referring URL, device type, or location, for instance, there may be a correlation between this data and items preferred. The UR can detect this **if** it exists and recommend based on this context, even to new users. We call this "micro-segmented" recommendations since they are not personal but group users based on limited contextual information. These will not be as good as when more is know about the user but may be better than simply returning popular items.
* It includes a fallback to some form of item popularity when there is no other information known about the user (not implemented in v0.1.0).
* All of the above can be mixed into a single query for blended results and so the query can be tuned to a great many applications. Also since only one query is made and boosting is supported, a query can be constructed with several fallbacks. Usage data is most important so boost that high, micro-segemnting data may be better than popularity so boost that lower, and popularity fills in if no other recommendations are available.

**Other features**:

 * Makes recommendations based on realtime user history. Even anonymous users will get recommendations if they have recorded preference history and a user-id. There is no hard requirement to retrain the model to make this happen. 

##Configuration, Events, and Queries
###Biases

These take the form of boosts and filters where a neutral bias is 1.0. The importance of some part of the query may be boosted by a positive non-zero float. If the bias is < 0 it is considered a filter&mdash;meaning no recommendation is made that lacks the filter value(s). One example of a filter is where it may make sense to show only "electronics" recommendations when the user is viewing an electronics product. Biases are often applied to a list of data, for instance the user is looking at a video page with a cast of actors. The "cast" list is metadata attached to items and a query can show "people who liked this, also liked these" type recs but also include the current cast boosted by 0.5. This can be seen as showing similar item recs but using the cast in the query in a way that will not overpower the similar items (since by default they have a neutral 1.0 boost).


###Engine.json

This file allows the user to describe and set parameters that control the engine operations. Some of the parameters work as defaults values for every query and can be overridden or added to in the query.

    {
      "id": "default",
      "description": "Default settings",
      "engineFactory": "org.template.RecommendationEngine",
      "datasource": {
        "params" : {
          "name": "sample-movielens",
          "appName": "URApp1",
          "eventNames": ["buy", "view"] // name your events with any string
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
          "name": "ur",
          "params": {
            "appName": "URApp1",
            "indexName": "urindex",
            "typeName": "items",
            "eventNames": ["buy", "view"]
            "blacklistEvents": ["buy", "view"],
            "maxEventsPerEventType": 100,
            "maxCorrelatorsPerEventType": 50,
            "maxQueryEvents": 500,
            "num": 20,
            "userbias": -maxFloat..maxFloat,
            "itembias": -maxFloat..maxFloat,
            "returnSelf": true | false,
            “fields”: [
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

The “params” section controls most of the features of the UR. Possible values are:

* **appName**: required string describing the app using the engine. Must be the same as is seen with `pio app list`
* **indexName**: required string describing the index for all correlators, something like "urindex". The Elasticsearch URI for its REST interface is `http:/**your-master-machine**/indexName/typeName/...` You can access ES through its REST interface here.
* **typeName**: required string describing the type in Elasticsearch terminology, something like "items". This has no important meaning but must be part of the Elastic search URI for queries.
* **eventNames**: required array of string identifiers describing action events recorded for users, things like “purchase”, “watch”, “add-to-cart”, even “location”. or “device” can be considered actions and used in recommendations. The first action is to be considered primary, the others secondary for cooccurrence and cross-cooccurrence calculations. 
* **maxEventsPerEventType** optional, default = 500. Amount of usage history to keep use in model calculation.
* **maxCorrelatorsPerEventType**: optional, default = 50. An integer that controls how many of the strongest correlators are created for every event type named in `eventNames`.
* **maxQueryEvents**: optional, default = 100. An integer specifying the number of most recent primary actions used to make recommendations for an individual. More implies some will be less recent actions. Theoretically using the right number will capture the user’s current interests.
* **num**: optional, default = 20. An integer telling the engine the maximum number of recs to return per query but less may be returned if the query produces less results or post recs filters like blacklists remove some.
* **blacklistEvents**: optional, default = the primary action. An array of strings corresponding to the actions taken on items, which will cause them to be removed from recs. These will have the same values as some user actions - so “purchase” might be best for an ecom application since there is often little need to recommend something the user has already bought. If this is not specified then the primary event is assumed. To blacklist no event, specify an empty array. Note that not all actions are taken on the same items being recommended. For instance every time a user goes to a category page this could be recorded as a category preference so if this event is used in a blacklist it will have no effect, the category and item ids should never match. If you want to filter certain categories, use a field filter and specify all categories allowed.
* **fields**: optional, default = none. An array of default field based query boosts and filters applied to every query. The name = type or field name for metadata stored in the EventStore with $set and $unset events. Values = and array of one or more values to use in any query. The values will be looked for in the field name. Bias will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name.
* **userBias**: optional, default = none. Amount to favor user history in creating recs, 1 is neutral, and negative number means to use as a filter so the user history must be used i recs, any positive number greater than one will boost the importance of user history in recs.
* **itemBias**: optional, default = none. Same as userbias but applied to similar items to the item supplied in the query.
* **returnSelf**: optional, default = false. Boolean asking to include the item that was part of the query (if there was one) as part of the results. The default is false and this is by far the most common use so this is seldom required.

###Queries

Query fields determine what data is used to match when returning recs. Some fields have default values in engine.json and so may never be needed in individual queries. On the other hand all values from engine.json may be overridden or added to in an individual query. The only requirement is that there must be a user or item in every query.

    {
      “user”: “xyz”, 
      “userBias”: -maxFloat..maxFloat,
      “item”: “53454543513”, 
      “itemBias”: -maxFloat..maxFloat,  
      “num”: 4,
      "fields”: [
        {
          “name”: ”fieldname”
          “values”: [“fieldValue1”, ...],
          “bias”: -maxFloat..maxFloat 
        },...
      ]
      “blacklistItems”: [“itemId1”, “itemId2”, ...]
      "returnSelf": true | false,
    }

* **user**: optional, contains a unique id for the user. This may be a user not in the **training**: data, so a new or anonymous user who has an anonymous id. All user history captured in near realtime can be used to influence recommendations, there is no need to retrain to enable this.
* **userBias**: optional, the amount to favor the user's history in making recs. The user may be anonymous as long as the id is unique from any authenticated user. This tells the recommender to return recs based on the user’s event history. Used for personalized recommendations. Overrides and bias in engine.json
* **item**: optional, contains the unique item identifier
* **itemBias**: optional, the amount to favor similar items in making recs. This tells the recommender to return items similar to this the item specified. Use for “people who liked this also liked these”. Overrides any bias in engine.json
* **fields**: optional, array of fields values and biases to use in this query. The name = type or field name for metadata stored in the EventStore with $set and $unset events. Values = an array on one or more values to use in this query. The values will be looked for in the field name. Bias will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name.
* **num**: optional max number of recs to return. There is no guarantee that this number will be returned for every query. Adding backfill in the engine.json will make it much more likely to return this number of recs.
* **blacklistItems**: optional. Unlike the engine.json, which specifies event types this part of the query specifies individual items to remove from returned recs. It can be used to remove duplicates when items are already shown in a specific context. This is called anti-flood in recommender use.
* **returnSelf**: optional boolean asking to include the item that was part of the query (if there was one) as part of the results. Defaults to false.
 
All query params are optional, the only rule is that there must be an item or user specified. Defaults are either noted or taken from algorithm values, which themselves may have defaults. This allows very simple queries for the simple, most used cases.
 
The query returns personalized recommendations, similar items, or a mix including backfill. The query itself determines this by supplying item, user or both. Some examples are:

###Simple Non-contextual Personalized

	{
	  “user”: “xyz”
	}
	
This gets all default values from the engine.json and uses only action correlators for the types specified there.

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

This returns items based on user xyz history or similar to item 53454543513 but favoring user history recs. These are filtered by categories and boosted to favor more genre specific items. 

**Note**:This query should be considered **experimental**. mixing user history with item similarity is possible but may have unexpected results.

##Creating New Model with New Event Types

To begin using on new data with an engine that has been used with sample data or using different events follow these steps:

1. Create a new app name, change `appName` in `engine.json`
2. Run `pio app new **your-new-app-name**`
3. Make any changes to `engine.json` to specify new event names and config values. Make sure `"eventNames": ["**your-primary-event**", "**a-secondary-event**", "**another-secondary-event**", ...]` contains the exact string used for your events and that the primary one is first in the list.
4. Import new events or allow enough to accumulate into the EventStore. If you are using sample events from a file run `python data/**your-python-import-script**.py --access_key **your-access-key**` where the key can be retrieved with `pio app list`
5. Perform `pio build`, `pio train`, and `pio deploy`
6. Copy and edit the sample query script to match your new data. For new user ids pick a user that exists in the events, same for metadata `fields`, and items.
7. Run your edited query script and check the recs.

## Versions

### v0.1.0

 - user and item based queries supported
 - multiple usage events supported
 - filters and boosts supported on item properties and on user or item based results.
 - fast writing to Elasticsearch using Spark
 - convention over configuration for queries, defaults make simple/typical queries simple and overrides add greater expressiveness.

  
### Known issues

  - dates not implemented
  - index dropped then refreshed in `pio train` so no need to redeploy if the server is running. This violates conventions for other templates but is actually good. It means we have a hot-swapped model. If there are server timeouts during train, for the refresh response or for ongoing queries, we may need to find optimizations.
  - popularity fallback not implemented.

## References

 * Other documentation of the algorithm is [here](http://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html)
 * A free ebook, which talks about the general idea: [Practical Machine Learning](https://www.mapr.com/practical-machine-learning).
 * A slide deck, which talks about mixing actions and other correlator types, including content-based ones: [Creating a Unified Recommender](http://www.slideshare.net/pferrel/unified-recommender-39986309?ref=http://occamsmachete.com/ml/)
 * Two blog posts: What's New in Recommenders: part [#1](http://occamsmachete.com/ml/2014/08/11/mahout-on-spark-whats-new-in-recommenders/) [#2](http://occamsmachete.com/ml/2014/09/09/mahout-on-spark-whats-new-in-recommenders-part-2/)
 * A post describing the log-likelihood ratio: [Surprise and Coincidence](http://tdunning.blogspot.com/2008/03/surprise-and-coincidence.html) LLR is used to reduce noise in the data while keeping the calculations O(n) complexity.
