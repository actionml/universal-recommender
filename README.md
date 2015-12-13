# Universal Recommendation Template

The Universal Recommender (UR) is a Cooccurrence type that creates correlators from several user actions, events, or profile information and performs the recommendations query with a Search Engine. It also supports item properties for filtering and boosting recommendations. This allows users to make use of any part of their user's clickstream or even profile and context information in making recommendations. TBD: several forms of popularity type backfill and content-based correlators for content based recommendations. Also filters on property date ranges. With these additions it will more closely live up to the name "Universal"

##Quick Start
Check the prerequisites below before setup, it will inform choices made.

 1. [Install the PredictionIO framework](https://docs.prediction.io/install/) **be sure to choose HBase and Elasticsearch** for storage. This template requires Elasticsearch.
 2. Make sure the PIO console and services are running, check with `pio status`
 3. [Install this template](https://docs.prediction.io/start/download/) with `pio template get PredictionIO/template-scala-parallel-universal-recommendation`
 
###Import Sample Data

1. Create a new app name, change `appName` in `engine.json`
2. Run `pio app new **your-new-app-name**`
4. Import sample events by running `python examples/import_handmade.py --access_key **your-access-key**` where the key can be retrieved with `pio app list`
3. The engine.json file in the root directory of your new UR template is set up for the data you just imported (make sure to create a new one for your data) Edit this file and change the `appName` parameter to match what you called the app in step #2
5. Perform `pio build`, `pio train`, and `pio deploy`
6. To execute some sample queries run `./examples/query-handmade.sh`

##Important Notes for the Impatient

 - The Universal Recommender v0.2.0+ requires PredictionIO v0.9.5+
 - When sending events through the SDK, REST API, or importing it is required that all usage/preference events are named in the engine.json and **there must be data for the first named event** otherwise there will be **no model created** and errors will occur during traing.
 - When sending usage events it is required that the entityType is "user" and targetEntityType is "item". The type of the item is inferred from the event names, which must be one of the eventNames in the engine.json.
 - **Elasticsearch**: The UR **requires Eleasticsearch** since it performs the last step in the algorithm. It will store the model created at `pio train` time.
 - **EventStore**: The EventServer may use another DB than HBase but has been most heavilty tested with HBase.
 
##What is a Universal Recommender

The Universal Recommender (UR) will accept a range of data, auto correlate it, and allow for very flexible queries. The UR is different from most recommenders in these ways:

* It takes a single very strong "primary" event type&mdash;one that clearly reflects a user's preference&mdash;and correlates any number of other "secondary" event types. user profile data, and user context data to the primary event. This has the effect of using virtually anything we know about the user to recommend the items attached to the primary event. Much of a user’s clickstream can be used to make recommendations. If a user has no history of the primary action (purchase for instance) but does have history of the secondary data, personalized recommendations for purchases can still be made. With user purchase history the recommendations become better. This is very important because it means better recommendatons for more users than typical recommenders.
* It can boost and filter based on events or item metadata/properties. This means it can give personalized recommendations that are biased toward “SciFi” and filtered to only include “Promoted” items when the business rules call for this.
* It can use a user's context to make recommendations even when the user is new. If usage data has been gathered for other users for referring URL, device type, or location, for instance, there may be a correlation between this data and items preferred. The UR can detect this if it exists and recommend based on this context, even to new users. We call this "micro-segmented" recommendations since they are not personal but group users based on limited contextual information. These will not be as good as when more behavioral information is know about the user but may be better than simply returning popular items.
* It includes a fallback to some form of item popularity when there is no other information known about the user. Backfill types include popular, trending, and hot. Backfill can be boosted or filtered by item metadata just as any recommendation.
* All of the above can be mixed into a single query for blended results and so the query can be tuned to a great many applications without special data or separate models.
* Realtime user history is used in all recommendations. Even anonymous users will get recommendations if they have recorded preference history and a user-id. There is no  requirement to "retrain" the model to make this happen. The rule of thumb is to retrain based on frequency of adding new items. So for breaking-news articles you may want to retrain frequently but for ecom once a day would be fine. In either case realtime user behavior affects recommendations.

###Typical Uses:
* **Personalized Recommendations**
* **Similar Item Recommendations**: "people who liked this also like these"
* **Shopping Cart Recommendations**:  more generally item-set recommendations. This can be applied to wishlists, watchlists, likes, any set of items that may go together.
* **Popular Items**: These can even be the primary form of recommendation if desired for some applications since serveral forms are supported. By default if a user has no recommendations popular items will backfill to achieve the number required.
* **Hybrid Collaborative Filtering and Content-based Recommendations**: since item properties can boost or filter recommendations and can often also be treated as secondary user preference data a smooth blend of usage and content can be achieved. 

##Configuration, Events, and Queries

###Primary and Secondary Data

**There must be a "primary" event/action recorded for some number of users**. This action defines the type of item returned in recommendations and is the measure by which all secondary data is measured. More technically speaking all secondary data is tested for correlation to the primary event. Secondary data can be anything that you may think of as giveing some insight into the user. If something in the secondary data has no correlation to the primary event it will have no effect on recommendations. For instance in an ecom setting you may want "buy" as a primary event. There may be many (but none is also fine) seconday events like (user-id, device-preference, device-id). This can be thought of as a user's device preference and recorded at all logins. If this doesn not correlate to items bought it will not effect recommendations. 

###Biases

These take the form of boosts and filters where a neutral bias is 1.0. The importance of some part of the query may be boosted by a positive non-zero float. If the bias is < 0 it is considered a filter&mdash;meaning no recommendation is made that lacks the filter value(s). One example of a filter is where it may make sense to show only "electronics" recommendations when the user is viewing an electronics product. Biases are often applied to a list of data, for instance the user is looking at a video page with a cast of actors. The "cast" list is metadata attached to items and a query can show "people who liked this, also liked these" type recommendations but also include the current cast boosted by 0.5. This can be seen as showing similar item recommendations but using the cast members in a way that will not overpower the similar items (since by default they have a neutral 1.0 boost). The result would be similar items favoring ones with simliar cast members.

###Dates

Dates can be used to filter recommendations in one of two ways, where the data range is attached to items or is specified in the query:

 1. The date range can be attahed to every item and checked against the current date. The current date can be in the query or defaults to the current prediction server date. This mode requires that all items have a upper and lower date attached to them as a property. It is designed to be something like an "available after" and "expired after". The default check against server date is triggered when the expireDateName and availableDateName are both specified but no date is passed in with the query. **Note**: Both dates must be attached to items or they will not be recommended. To have one-sided filter make the avialable date some time far in the past and/or the expire date some time far in the future.
 2. A "dateRange" can be specified in the query and the recommended items will have a date that lies between the range dates.
 
###Engine.json

This file allows the user to describe and set parameters that control the engine operations. Many values have defaults so the following can be seen as the minimum for an ecom app with only one "buy" event. Reasonable defaults are used so try this first and add tunings or new event types and item property fields as you become more familiar.

####Simple Default Values
    {
	  "comment":" This config file uses default settings for all but the required values see README.md for docs",
	  "id": "default",
	  "description": "Default settings",
	  "engineFactory": "org.template.RecommendationEngine",
	  "datasource": {
	    "params" : {
	      "name": "sample-handmade-data.txt",
	      "appName": "handmade",
	      "eventNames": ["purchase", "view"]
	    }
	  },
	  "sparkConf": {
	    "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
	    "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
	    "spark.kryo.referenceTracking": "false",
	    "spark.kryoserializer.buffer.mb": "300",
	    "spark.kryoserializer.buffer": "300m",
	    "spark.executor.memory": "4g",
	    "es.index.auto.create": "true"
	  },
	  "algorithms": [
	    {
	      "comment": "simplest setup where all values are default, popularity based backfill, must add eventsNames",
	      "name": "ur",
	      "params": {
	        "appName": "handmade",
	        "indexName": "urindex",
	        "typeName": "items",
	        "comment": "must have data for the first event or the model will not build, other events are optional",
	        "eventNames": ["purchase", "view"]
	      }
	    }
	  ]
	}
	


####Complete Parameter Set

A full list of tuning and config parameters is below. See the field description for specific meaning. Some of the parameters work as defaults values for every query and can be overridden or added to in the query. 

**Note:** It is strongly advised that you try the default/simple settings first before changing them. The possible exception is adding secondary events in the `eventNames` array. 

    {
      "id": "default",
      "description": "Default settings",
      "comment": "replace this with your JVM package prefix, like org.apache",
      "engineFactory": "org.template.RecommendationEngine",
      "datasource": {
        "params" : {
          "name": "some-data",
          "appName": "URApp1",
          "eventNames": ["buy", "view"]
        }
      },
      “comment”: “This is for Mahout and Elasticsearch, the values are minimums and should not be removed”,
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
            "seed": 3,
            "recsModel": "all",
			"backfillField": {
  				"backfillType": "popular",
  				"eventnames": ["buy", "view"],
  				"duration": 259200
  			},
            "expireDateName": "expireDateFieldName",
            "availableDateName": "availableDateFieldName",
            "dateName": "dateFieldName",
            "userbias": -maxFloat..maxFloat,
            "itembias": -maxFloat..maxFloat,
            "returnSelf": true | false,
            “fields”: [
              {
                “name”: ”fieldname”,
                “values”: [“fieldValue1”, ...],
                “bias”: -maxFloat..maxFloat,
              },...
            ]
          }
        }
      ]
    }

The “params” section controls most of the features of the UR. Possible values are:

* **appName**: required string describing the app using the engine. Must be the same as is seen with `pio app list`
* **indexName**: required string describing the index for all correlators, something like "urindex". The Elasticsearch URI for its REST interface is `http:/**elasticsearch-machine**/indexName/typeName/...` You can access ES through its REST interface here.
* **typeName**: required string describing the type in Elasticsearch terminology, something like "items". This has no important meaning but must be part of the Elasticsearch URI for queries.
* **eventNames**: required array of string identifiers describing action events recorded for users, things like “purchase”, “watch”, “add-to-cart”, even “location”, or “device” can be considered actions and used in recommendations. The first action is to be considered the primary action because it **must** exist in the data and is considered the strongest indication of user preference for items, the others are secondary for cooccurrence and cross-cooccurrence calculations. The secondary actions/events may or may not have target entity ids that correspond to the items to be recommended, so they are allowed to be things like category-ids, device-ids, location-ids... For example: a category-pref event would have a category-id as the target entity id but a view would have an item-id as the target entity id (see Events below). Both work fine as long as all usage events are tied to users. 
* **maxEventsPerEventType** optional (use with great care), default = 500. Amount of usage history to keep use in model calculation.
* **maxCorrelatorsPerEventType**: optional (use with great care), default = 50. An integer that controls how many of the strongest correlators are created for every event type named in `eventNames`.
* **maxQueryEvents**: optional (use with great care), default = 100. An integer specifying the number of most recent primary actions used to make recommendations for an individual. More implies some will be less recent actions. Theoretically using the right number will capture the user’s current interests.
* **num**: optional, default = 20. An integer telling the engine the maximum number of recommendations to return per query but less may be returned if the query produces less results or post recommendations filters like blacklists remove some.
* **blacklistEvents**: optional, default = the primary action. An array of strings corresponding to the actions taken on items, which will cause them to be removed from recommendations. These will have the same values as some user actions - so “purchase” might be best for an ecom application since there is often little need to recommend something the user has already bought. If this is not specified then the primary event is assumed. To blacklist no event, specify an empty array. Note that not all actions are taken on the same items being recommended. For instance every time a user goes to a category page this could be recorded as a category preference so if this event is used in a blacklist it will have no effect, the category and item ids should never match. If you want to filter certain categories, use a field filter and specify all categories allowed.
* **fields**: optional, default = none. An array of default field based query boosts and filters applied to every query. The name = type or field name for metadata stored in the EventStore with $set and $unset events. Values = and array of one or more values to use in any query. The values will be looked for in the field name. Bias will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name.
* **userBias**: optional (use with great care), default = none. Amount to favor user history in creating recommendations, 1 is neutral, and negative number means to use as a filter so the user history must be used in recommendations, any positive number greater than one will boost the importance of user history in recommendations.
* **itemBias**: optional (use with great care), default = none. Same as userbias but applied to similar items to the item supplied in the query.
* **expireDateName** optional, name of the item properties field that contains the date the item expires or is unavailable to recommend.
* **availableDateName** optional, name of the item properties field that contains the date the item is available to recommend. 
* **dateName** optional, a date or timestamp used in a `dateRange` recommendations filter.
* **returnSelf**: optional, default = false. Boolean asking to include the item that was part of the query (if there was one) as part of the results. The default is false and this is by far the most common use so this is seldom required.
* **recsModel** optional, default = "all", which means  collaborative filtering with popular items returned when no other recommendations can be made. Otherwise: "all", "collabFiltering", "backfill". If only "backfill" is specified then the train will create only some backfill type like popular. If only "collabFiltering" then no backfill will be included when there are no other recommendations.
* **backfillField** optional (use with great care), default: backfillType = popular, eventNames = only the first/primary event in `eventNames`, corresponding to the primary action, duration = 259200, which is the number of seconds in a 3 days. The primary/first event used for recommendations is always attached to items you wish to recommend, the other events are not neccessarily attached to the same items. If events like "category-preference" are used then popular categories will be calculated and this will have no effect for backfill. Possible backfillTypes are "popular", "trending", and "hot", which correspond to the number of events in the duration, the average event velocity or the average event acceleration over the time indicated. This is calculated for every event and is used to rank them and so can be used with biasing metadata so you can get, for instance, hot items in some category. **Note**: when using "hot" the algorithm divides the events into three periods and since event tend to be cyclical by day, 3 days will produce results mostly free of daily effects for all types. Making this time period smaller may cause odd effects from time of day the algorithm is executed. Popular is not split and trending splits the events in two. So choose the duration accordingly. 
* **seed** Set this if you want repeatable downsampling for some offline tests. This can be ignored and shouldn't be set in production. 

###Queries

####Simple Personalized Query

	{
	  “user”: “xyz”
	}
	
This gets all default values from the engine.json and uses only action correlators for the types specified there.

####Simple Similar Items Query

	{
	  “item”: “53454543513”   
	}
	
This returns items that are similar to the query item, and blacklist and backfill are defaulted to what is in the engine.json

####Full Query Parameters

Query fields determine what data is used to match when returning recommendations. Some fields have default values in engine.json and so may never be needed in individual queries. On the other hand all values from engine.json may be overridden or added to in an individual query. The only requirement is that there must be a user or item in every query.

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
      "dateRange": {
        "name": "dateFieldname",
        "beforeDate": "2015-09-15T11:28:45.114-07:00",
        "afterDate": "2015-08-15T11:28:45.114-07:00"
      },
      "currentDate": "2015-08-15T11:28:45.114-07:00",
      “blacklistItems”: [“itemId1”, “itemId2”, ...]
      "returnSelf": true | false,
    }

* **user**: optional, contains a unique id for the user. This may be a user not in the **training**: data, so a new or anonymous user who has an anonymous id. All user history captured in near realtime can be used to influence recommendations, there is no need to retrain to enable this.
* **userBias**: optional (use with great care), the amount to favor the user's history in making recommendations. The user may be anonymous as long as the id is unique from any authenticated user. This tells the recommender to return recommendations based on the user’s event history. Used for personalized recommendations. Overrides and bias in engine.json.
* **item**: optional, contains the unique item identifier
* **itemBias**: optional (use with great care), the amount to favor similar items in making recommendations. This tells the recommender to return items similar to this the item specified. Use for “people who liked this also liked these”. Overrides any bias in engine.json
* **fields**: optional, array of fields values and biases to use in this query. The name = type or field name for metadata stored in the EventStore with $set and $unset events. Values = an array on one or more values to use in this query. The values will be looked for in the field name. Bias will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name.
* **num**: optional max number of recommendations to return. There is no guarantee that this number will be returned for every query. Adding backfill in the engine.json will make it much more likely to return this number of recommendations.
* **blacklistItems**: optional. Unlike the engine.json, which specifies event types this part of the query specifies individual items to remove from returned recommendations. It can be used to remove duplicates when items are already shown in a specific context. This is called anti-flood in recommender use.
* **dateRange** optional, default is not range filter. One of the bound can be omitted but not both. Values for the `beforeDate` and `afterDate` are strings in ISO 8601 format. A date range is ignored if **currentDate** is also specified in the query.
* **currentDate** optional, must be specified if used. Overrides the **dateRange** is both are in the query.
* **returnSelf**: optional boolean asking to include the item that was part of the query (if there was one) as part of the results. Defaults to false.
 
All query params are optional, the only rule is that there must be an item or user specified. Defaults are either noted or taken from algorithm values, which themselves may have defaults. This allows very simple queries for the simple, most used cases.
 
The query returns personalized recommendations, similar items, or a mix including backfill. The query itself determines this by supplying item, user or both. Some examples are:

###Contextual Personalized

	{
	  “user”: “xyz”,
	  “fields”: [
	    {
	      “name”: “categories”
	      “values”: [“series”, “mini-series”],
	      “bias”: -1 // filter out all except ‘series’ or ‘mini-series’
	    },{
	      “name”: “genre”,
	      “values”: [“sci-fi”, “detective”]
	      “bias”: 1.02 // boost/favor recommendations with the `genre’ = `sci-fi` or ‘detective’
	    }
	  ]
	}

This returns items based on user "xyz" history filtered by categories and boosted to favor more genre specific items. The values for fields have been attached to items with $set events where the “name” corresponds to a doc field and the “values” correspond to the contents of the field. The “bias” is used to indicate a filter or a boost. For Solr or Elasticsearch the boost is sent as-is to the engine and it’s meaning is determined by the engine (Lucene in either case). As always the blacklist and backfill use the defaults in engine.json.

###Date ranges as query filters
When the a date is stored in the items properties it can be used in a date range query. This is most often used by the app server since it may know what the range is, while a client query may only know the current date and so use the "Current Date" filter below.

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
	      “bias”: 1.02 // boost/favor recommendations with the `genre’ = `sci-fi` or ‘detective’
	    }
	  ],
      "dateRange": {
        "name": "availabledate",
        "before": "2015-08-15T11:28:45.114-07:00",
        "after": "2015-08-20T11:28:45.114-07:00       
      }
	}
	

Items are assumed to have a field of the same `name` that has a date associated with it using a `$set` event. The query will return only those recommendations where the date field is in reange. Either date bound can be omitted for a on-sided range. The range applies to all returned recommendations, even those for popular items. 	

###Current Date as a query filter
When setting an available date and expire date on items, the current date can be used as a filter, the UR will check that the current date is before the expire date, and after or equal to the available date. You can use either expire date or available date or both. The names of these item fields is specified in the engin.json.

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
	      “bias”: 1.02	    }
	  ],
      "currentDate": "2015-08-15T11:28:45.114-07:00"  
	}

###Contextual Personalized with Similar Items

	{
	  “user”: “xyz”, 
	  "userBias": 2, // favor personal recommendations
	  “item”: “53454543513”, // fallback to contextual recommendations
	  “fields”: [
	    {
	      “name”: “categories”
	      “values”: [“series”, “mini-series”],
	      “bias”: -1 }// filter out all except ‘series’ or ‘mini-series’
	    },{
	      “name”: “genre”,
	      “values”: [“sci-fi”, “detective”]
	      “bias”: 1.02 // boost/favor recommendations with the `genre’ = `sci-fi` or ‘detective’
	    }
	  ]
	}

This returns items based on user xyz history or similar to item 53454543513 but favoring user history recommendations. These are filtered by categories and boosted to favor more genre specific items. 

**Note**:This query should be considered **experimental**. mixing user history with item similarity is possible but may have unexpected results. If you use this you should realize that user and item recommendations may be quite divergent and so mixing the them in query may produce nonsense. Use this only with the engine.json settings for "userbias" and "itembias" to favor one over the other.


###Popular Items

	{
	}

This is a simple way to get popular items. All returned scores will be 0 but the order will be based on relative popularity. Field-based biases for boosts and filters can also be applied.

##Events
The Universal takes in potentially many events. These should be seen as a primary evnet, which is a very clear indication of a user preference and secondary events that we think may tell us something about user "taste" in some way. The Universal Recommender is built on a distributed Correlation Engine so it will test that these secondary events actually relate to the primary one and those that do not correlate will have little or no effect on recommendations (though they will make it longer to train and get query results). It is recommended that you start with one ot two events and increase the number as you see how these events effect results and timing.

###Usage Events

Events in PredicitonIO are sent to the EventSever in the following form:

	{
		"event" : "purchase",
		"entityType" : "user",
		"entityId" : "1243617",
		"targetEntityType" : "item",
		"targetEntityId" : "iPad",
		"properties" : {},
		"eventTime" : "2015-10-05T21:02:49.228Z"
	}
	
This is what a "purchase" event looks like. Note that a usage event **always** is from a user and has a user id. Also the "targetEntityType" is always "item". The actual target entity is implied by the event name. So to create a "category-preference" event you would send something like this:

	{
		"event" : "category-preference",
		"entityType" : "user",
		"entityId" : "1243617",
		"targetEntityType" : "item",
		"targetEntityId" : "electronics",
		"properties" : {},
		"eventTime" : "2015-10-05T21:02:49.228Z"
	}
	
This event would be sent when the user clicked on the "electonics" category or perhaps purchased an item that was in the "electronics" category. Note that the "targetEntityType" is always "item".

###Property Change Events

To attach properties to items use a $set event like this:

	{
		"event" : "$set",
		"entityType" : "item",
		"entityId" : "ipad",
		"properties" : {
			"category": ["electronics", "mobile-phones"]
			"expireDate": "2016-10-05T21:02:49.228Z"
		},
		"eventTime" : "2015-10-05T21:02:49.228Z"
	}

Unless a property has a special meaning specified in the engine.json, like date values, the property is assumed to be an array of strings, which act as categorical tags. You can add things like "premium" to the "tier" property then later if the user is a subscriber you can set a filter that allows recommendations from `"tier": ["free", "premium"]` where a non subscriber might only get recommendations for `"tier": ["free"]`. These are passed in to the query using the `"fields"` parameter (see Contextual queries above).

Using properties is how boosts and filters are applied to recommended items. It may seem odd to treat a category as a filter **and** as a secondary event (category-preference) but the two pieces of data are used in quite different ways. As properties they bias the recommendations, when they are events they add to user data that returns recommendations. In other words as properties they work with boost and filter business rules as secondary usage events they show something about user taste to make recommendations better.


##Creating a New Model or Adding Event Types

To begin using new data with an engine that has been used with sample data or using different events follow these steps:

1. Create a new app name, backup your old `engine.json` and change `appName` in the new `engine.json`
2. Run `pio app new **your-new-app-name**`
3. Make any changes to `engine.json` to specify new event names and config values. Make sure `"eventNames": ["**your-primary-event**", "**a-secondary-event**", "**another-secondary-event**", ...]` contains the exact string used for your events and that the primary one is first in the list.
4. Import new events or allow enough to accumulate into the EventStore. If you are using sample events from a file run `python examples/**your-python-import-script**.py --access_key **your-access-key**` where the key can be retrieved with `pio app list`
5. Perform `pio build`, `pio train`, and `pio deploy`
6. Copy and edit the sample query script to match your new data. For new user ids pick a user that exists in the events, same for metadata `fields`, and items.
7. Run your edited query script and check the recommendations.

##Tests
**Integration test**: Once PIO and all servcies are running but before any model is deployed, run `./examples/integration-test` This will print a list of differences in the actual results from the expected results, none means the test passed. Not that the model will remain deployed and will have to be deployed over or killed by pid.

**Event name restricted query test**: this is for the feature that allows event names to be specified in the query. It restricts the user history that is used to create recommendations and is primarily for use with the MAP@k cross-validation test. The engine config removes the blacklisting of items so it must be used when doing MAP@k calculations. This test uses the simple sample data. Steps to try the test are: 

1. start pio and all services 
2. `pio app new handmade` 
3. `python examples/import_handmade.py --access_key <key-from-app-new>` 
4. `cp engine.json engine.json.orig` 
5. `cp event-names-test=engine.json engine.json`
5. `pio train`
6. `pio deploy` 
5. `./examples/single-eventNames-query.sh`
6. restore engine.json
7. kill the deployed prediction server

**MAP@k**: This tests the predictive power of each usage event/indicator. All eventNames used in queries must be removed from the blacklisted events in the engine.json used for a particular dataset. So if `"eventNames": ["purchase","view"]` is in the engine.json for the dataset, these events must be removed from the blacklist with `"blacklist": []`, which tells the engine to not blacklist items with `eventNames` for a user. Allowing blacklisting will artificially lower MAP@k and so not give the desired result.

## Versions

### v0.2.3

 - removed isEmpty calls that were taking an extremely long time to execute, results in considerable speedup. Now the vast majority of `pio train` time is taken up by writing to Elasticsearch. This can be optimized by creating and ES cluster or giving ES lots of memory.
 
### v0.2.2

 - a query with no item or user will get recommendations based on popularity
 - a new integration test has been added
 - a regression bug where some ids were being tokenized by Elasticsearch, leading to incorrect results, was fixed. **NOTE: for users with complex ids containing dashes or spaces this is an important fix.**
 - a dateRange in the query now takes precidence to the item attached expiration and avaiable dates. 

### v0.2.1

 - date ranges attached to items will be compared to the prediction servers current data if no date is provided in the query. 

### v0.2.0

 - date range filters implemented
 - hot/trending/popular used for backfill and when no other recommendations are returned by the query
 - filters/bias < 0 caused scores to be altered in v0.1.1 fixed in this version so filters have no effect on scoring.
 - the model is now hot-swapped in Elasticsearch so no downtime should be seen, in fact there is no need to run `pio deploy` to make the new model active.
 - it is now possible to have an engine.json (call it something else) dedicated to recalculating the popularity model. This allows fast updates to poularity without recalculating the collaborative filtering model.
 - Elasticsearch can now be in cluster mode

### v0.1.1

 - ids are now exact matches, for v0.1.0 the ids had to be lower case and were subject to tokenizing analysis so using that version is not recommended.

### v0.1.0

 - user and item based queries supported
 - multiple usage events supported
 - filters and boosts supported on item properties and on user or item based results.
 - fast writing to Elasticsearch using Spark
 - convention over configuration for queries, defaults make simple/typical queries simple and overrides add greater expressiveness.

### Known issues

 - see the github [issues list](https://github.com/PredictionIO/template-scala-parallel-universal-recommendation/issues)
 
## References

 * Other documentation of the algorithm is [here](http://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html)
 * A free ebook, which talks about the general idea: [Practical Machine Learning](https://www.mapr.com/practical-machine-learning).
 * A slide deck, which talks about mixing actions and other correlator types, including content-based ones: [Creating a Unified Recommender](http://www.slideshare.net/pferrel/unified-recommender-39986309?ref=http://occamsmachete.com/ml/)
 * Two blog posts: What's New in Recommenders: part [#1](http://occamsmachete.com/ml/2014/08/11/mahout-on-spark-whats-new-in-recommenders/) [#2](http://occamsmachete.com/ml/2014/09/09/mahout-on-spark-whats-new-in-recommenders-part-2/)
 * A post describing the log-likelihood ratio: [Surprise and Coincidence](http://tdunning.blogspot.com/2008/03/surprise-and-coincidence.html) LLR is used to reduce noise in the data while keeping the calculations O(n) complexity.
 
#License
This Software is licensed under the Apache Software Foundation version 2 licence found here: http://www.apache.org/licenses/LICENSE-2.0