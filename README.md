#The Universal Recommender

The Universal Recommender (UR) is a new type of collaborative filtering recommender based on an algorithm that can use data from a wide variety of user taste indicators&mdash;it is called the Correlated Cross-Occurrence algorithm. Unlike the matrix factorization embodied in things like MLlib's ALS, CCO is able to ingest any number of user actions, events, profile data, and contextual information. It then serves results in a fast and scalable way. It also supports item properties for filtering and boosting recommendations and can therefor be considered a hybrid collaborative filtering and content-based recommender. 

The use of multiple **types** of data fundamentally changes that way a recommender is used and, when employed correctly, will provide a significant increase in quality of recommendations vs. using only one user event. Most recommenders, for instance, can only use "purchase" events. Using all we know about a user and their context allows us to much better predict their preferences.

##Quick Start

The Universal Recommender requires the ActionML fork of PredictionIO. So follow these instalation instructions:

 1. [Install the PredictionIO framework](https://docs.prediction.io/install/) using one of [these setup guides](https://github.com/actionml/cluster-setup).
 2. Make sure the PIO console and services are running, check with `pio status`
 3. Check that HDFS and Spark are also running since `pio status` does not check this.
 3. Install the Universal Recommender as a PredictionIO Template with git. If you have git installed do the following:
 
 	```
 	$ git clone https://github.com/actionml/template-scala-parallel-universal-recommendation.git universal
 	```
 	
 	This will put the Universal Recommender code in `~/universal` With pio installed and running (make sure `pio status` looks good) perform the integration test with the following:
 	
 	```
 	$ sudo apt-get install python-pip # or other method for OS X
 	$ pip install predictionio datetime # to enable python test scripts
 	$ ./examples/integration-test
 	```
 	
 	This will import data into pio, build the Universal Recommender code, train on the data, deploy the UR server, make sample queries, compare them with expected results, and restore the engine.json to the original, leaving you ready to create your own app with tested config and code.
 	
 	**Note**: a diff will be printed and the ordering of items may be different as long as the score is the same the test passes.
 
###Import Sample Data

1. Create a new app name, change `appName` in `engine.json`
2. Run `pio app new **your-new-app-name**`
4. Import sample events by running `python examples/import_handmade.py --access_key **your-access-key**` where the key can be retrieved with `pio app list`
3. The engine.json file in the root directory of your new UR template is set up for the data you just imported (make sure to create a new one for your data) Edit this file and change the `appName` parameter to match what you called the app in step #2
5. Perform `pio build`, `pio train`, and `pio deploy`
6. To execute some sample queries run `./examples/query-handmade.sh`

##Important Notes for the Impatient

 - The Universal Recommender v0.3.0+ requires the ActionML fork of PredictionIO v0.9.6+
 - When sending events through the SDK, REST API, or importing it is required that all usage/preference events are named in the engine.json and **there must be data for the first named event** otherwise there will be **no model created** and errors will occur during training (negative array index error).
 - When sending usage events it is required that the entityType is "user" and targetEntityType is "item". The type of the item is inferred from the event names, which must be one of the eventNames in the engine.json.
 - **Elasticsearch**: The UR **requires Eleasticsearch** since it performs the last step in the algorithm. It will store the model created at `pio train` time.
 - **EventStore**: The EventServer may use another DB than HBase but has been most heavily tested with HBase.
 
##What is a Universal Recommender

The Universal Recommender (UR) will accept a range of data, auto correlate it, and allow for very flexible queries. The UR is different from most recommenders in these ways:

* It takes a single very strong "primary" indicator&mdash;one that clearly reflects a user's preference&mdash;and correlates any number of other "secondary" indicators. These can be user clickstream data, inferred preferences for categories or genres (what category or genre were the items the user bought, watched, read, or listened to), user profile data (gender, age-bracket), and user context data (location, device used). This has the effect of using virtually anything we know about the user to make recommendations. If a user has no history of the primary action (purchase for instance) but does have history of the secondary indicators then personalized recommendations can still be made. With user purchase history the recommendations become better. This is very important because it means better recommendatons for more users than typical recommenders.
* It can boost and filter based on events or item metadata/properties. This means it can give personalized recommendations that are biased toward “SciFi” and filtered to only include “Promoted” items when the business rules call for this.
* It can use a user's context to make recommendations even when the user is new. If usage data has been gathered for other users for referring URL, device type, or location, for instance, there may be a correlation between this data and items preferred. The UR can detect this and recommend based on this context. We call this "micro-segmented" recommendations since they are not personal but group users based on limited contextual or segmentation information. These will not be as good as when more behavioral information is know about the individual user but are often better than simply returning popular items.
* It includes a fallback to some form of item popularity when there is no other information known about the user. Backfill types include popular, trending, and hot. Popular items can be boosted or filtered by item metadata just as any recommendation.
* All of the above can be mixed into a single query for blended results and so the query can be tuned to a great many applications without special data or separate models.
* Realtime user history is used in all recommendations. Even anonymous users will get recommendations if they have recorded preference history and a user-id. There is no  requirement to "retrain" the model to make this happen. The rule of thumb is to retrain based on frequency of adding new items. So for breaking-news articles you may want to retrain frequently but for ecom once a day or week would be fine. In either case realtime user behavior affects recommendations.

###Typical Uses:
* **Personalized Recommendations**: "just for you", when you have user history
* **Similar Item Recommendations**: "people who liked this also like these"
* **Shopping Cart Recommendations**:  more generally item-set recommendations. This can be applied to wishlists, watchlists, likes, any set of items that may go together. Some also call this "complimentary purchase" recommendations.
* **Popular Items**: These can even be the primary form of recommendation if desired for some applications since serveral forms are supported. By default if a user has no recommendations popular items will backfill to achieve the number required.
* **Hybrid Collaborative Filtering and Content-based Recommendations**: since item properties can boost or filter recommendations and can often also be treated as secondary user preference data a smooth blend of usage and content can be achieved. 

##Configuration, Events, and Queries

### Start Here: Find Your Primary Indicator

To start using a recommender you must have a primary indicator of user preference so ask yourself 2 questions:

 1. *"What item type do I want to recommend?"* For ecom this will be a product, for media it will be a story or video.
 2. *"Of all the data I have what is the most pure and unambiguous indication of user preference for this item?"* For ecom a "buy" is good, for media a "read" or "watch90" (for watching 90% of a video). Try to avoid ambiguous things like ratings, after all what does a rating of 3 mean for a 1-5 range? Does a rating of 2 mean a like or dislike? If you have ratings then 4-5 is a pretty unambiguous "like" so the other ratings may not apply to a primary indicator&mdash;though they may still be useful so read on.
 
Take the item from #1, the indicator-name from #2 and the user-id and you have the data to create a "primary indicator" of the form **(user-id, "indicator-name", item-id)**. 

###Secondary Indicators

**There must be a "primary indicator" recorded for some number of users**. This  defines the type of item returned in recommendations and is the thing by which all secondary data is measured. More technically speaking all secondary data is tested for correlation to the primary indicator. Secondary data can be anything that you may think of as giving some insight into the user's taste. If something in the secondary data has no correlation to the primary indicator it will have no effect on recommendations. For instance in an ecom setting you may want "buy" as a primary event. There may be many (but none is also fine) secondary events like (user-id, device-preference, device-id). This can be thought of as a user's device preference and recorded at all logins. If this does not correlate to items bought it will not effect recommendations. 

###Biases

These take the form of boosts and filters where a neutral bias is 1.0. The importance of some part of the query may be boosted by a positive non-zero float. If the bias is < 0 it is considered a filter&mdash;meaning no recommendation is made that lacks the filter value(s). 

Think of bias as a multiplier to the score of the items that meet the condition so if bias = 2, and item-1 meets the condition, then multiply item-1's score times the bias. After all biases are applied the recommendations are returned ranked by score. The effect of bias is to:

 - Bias from 0 to < 1 : lower ranking for items that match the condition
 - Bias = 1: no effect
 - Bias > 1: raise the ranking for items that match the condition.
 - Bias < 0: A negative bias will **only** return items that meet the condition, so in other words it filters out any that do not meet all the conditions

One example of a filter is where it may make sense to show only "electronics" recommendations when the user is viewing an electronics product. Biases are often applied to a list of data, for instance the user is looking at a video page with a cast of actors. The "cast" list is metadata attached to items and a query can show "people who liked this, also liked these" type recommendations but also include the current cast boosted by 1.01. This can be seen as showing similar item recommendations but using the cast members to gently boost the similar items (since by default they have a neutral 1.0 boost). The result would be similar items favoring ones with similar cast members.

###Dates

Dates are only used for filters but apply in all recommendation modes including popularity quereies. Dates can be used to filter recommendations in one of two ways, where the date range is attached to items or is specified in the query:

 1. The date range can be attahed to every item and checked against the current date or a date passed in with the query. If not in the query the date to check against the item's range is the predoiction server date. This mode requires that all items have a upper and lower dates attached to them as a property. It is designed to be something like an "available after" and "expired after". **Note**: Both dates must be attached to items or they will not be recommended. To have one-sided filter make the avialable date some time far in the past and/or the expire date some time in the far future.
 2. A "dateRange" can be specified in the query and the recommended items must have a date that lies between the range dates. If items do not have a date property attached to them they will never be returned by a dateRange filtered query.
 
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
          "eventWindow": {
	        "duration": "3650 days",
            "removeDuplicates": false,
            "compressProperties": false
	      }
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
            "maxEventsPerEventType": 500,
            "maxCorrelatorsPerEventType": 50,
            "maxQueryEvents": 100,
            "num": 20,
            "seed": 3,
            "recsModel": "all",
			"backfillField": {
				"name": "popRank"
  				"backfillType": "popular",
  				"eventNames": ["buy", "view"],
  				"duration": "3 days",
  				"endDate": "ISO8601-date" //most recent date to end the duration
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
    
####Datasource Parameters

The `datasource: params:` section controls input data. This section is Algorithm independent and is meant to manage the size of data in the EventServer and do compaction. Is changes the persisted state of data. A fixed `timeWindow: duration:` will have the effect of making the UR calculate a model in a fixed amount of time as long as soon as there are enough events to start dropping old ones.

 - **eventNames**: enumerates the event types to be used, the first of which is the primary event.
 - **eventWindow**: This is optional and controls how much of the data in the EventServer to keep and how to compress events. The default it to not have a time window and do no compression. This will compact and drop old events from the EventServer permanently in the persisted data&mdash;so make sure to have some other archive of events it you are playing with the `timeWindow: duration:`.
	 - **duration**: This is parsed for "days", "hours", "minutes", or smaller periods and becomes a Scala `Duration` object defining the time from now backward to the point where older events will be dropped. $set property change event are never dropped.
	 - **removeDuplicates** a boolean telling the Datasource to de-duplicate non$set type events, defaults to `false`.
	 - **compressProperties**: a boolean telling the Datasource to compress property change event into one event expressing the current state of all properties, defaults to `false`.

####Algorithm Parameters

The `Algorithm: params:` section controls most of the features of the UR. Possible values are:

* **appName**: required string describing the app using the engine. Must be the same as is seen with `pio app list`
* **indexName**: required string describing the index for all correlators, something like "urindex". The Elasticsearch URI for its REST interface is `http:/**elasticsearch-machine**/indexName/typeName/...` You can access ES through its REST interface here.
* **typeName**: required string describing the type in Elasticsearch terminology, something like "items". This has no important meaning but must be part of the Elasticsearch URI for queries.
* **eventNames**: required array of string identifiers describing action events recorded for users, things like “purchase”, “watch”, “add-to-cart”, even “location”, or “device” can be considered actions and used in recommendations. The first action is to be considered the primary action because it **must** exist in the data and is considered the strongest indication of user preference for items, the others are secondary for cooccurrence and cross-cooccurrence calculations. The secondary actions/events may or may not have target entity ids that correspond to the items to be recommended, so they are allowed to be things like category-ids, device-ids, location-ids... For example: a category-pref event would have a category-id as the target entity id but a view would have an item-id as the target entity id (see Events below). Both work fine as long as all usage events are tied to users. 
* **maxEventsPerEventType** optional (use with great care), default = 500. Amount of usage history to keep use in model calculation.
* **maxCorrelatorsPerEventType**: optional (use with great care), default = 50. An integer that controls how many of the strongest correlators are created for every event type named in `eventNames`.
* **maxQueryEvents**: optional (use with great care), default = 100. An integer specifying the number of most recent user history events used to make recommendations for an individual. More implies some will be less recent actions. Theoretically using the right number will capture the user’s current interests.
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
* **backfillField** optional (use with great care), this set of parameters defines the calculation of the popularity model that ranks all items by their events in one of three different ways corresponding to: event counts (popular), change in event counts over time (trending), and change in trending over time (hot). If there are not enough recommendations to return teh number asked for, popular items will fill in the remaining recommendations asked for&mdash;hence the term "backfill". Purely popular items may be requested in the query by specifying no user of item.
	* **name** give the field a name in the model and defaults to "popRank"
	* **backfillType**  "popular", "trending", and "hot". These are event counts, velocity of change in event counts, or the acceleration of event counts. **Note**: when using "hot" the algorithm divides the events into three periods and since events tend to be cyclical by day, 3 days will produce results mostly free of daily effects for all types. Making this time period smaller may cause odd effects from time of day the algorithm is executed. Popular is not split and trending splits the events in two. So choose the duration accordingly. 
	* **eventNames** an array of eventNames to use in calculating the popularity model, this defaults to the single primary events&mdash;the first in the `algorithm: eventNames:` 
	* **duration** a duration like "3 days" (which is the default), which defines the time from now back to the last event to count in the popularity calculation.
	* **endDate** an ISO8601-date string to use to start the duration&mdash;the first event to count. This is almost never used live system but may be used in tests on batch imported events.
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
* **fields**: optional, array of fields values and biases to use in this query. 
	* **name** field name for metadata stored in the EventStore with $set and $unset events.
	* **values** an array on one or more values to use in this query. The values will be looked for in the field name. 
	* **bias** will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name. See **Biases** above.
* **num**: optional max number of recommendations to return. There is no guarantee that this number will be returned for every query. Adding backfill in the engine.json will make it much more likely to return this number of recommendations.
* **blacklistItems**: optional. Unlike the engine.json, which specifies event types this part of the query specifies individual items to remove from returned recommendations. It can be used to remove duplicates when items are already shown in a specific context. This is called anti-flood in recommender use.
* **dateRange** optional, default is not range filter. One of the bound can be omitted but not both. Values for the `beforeDate` and `afterDate` are strings in ISO 8601 format. A date range is ignored if **currentDate** is also specified in the query.
* **currentDate** optional, must be specified if used. Overrides the **dateRange** is both are in the query.
* **returnSelf**: optional boolean asking to include the item that was part of the query (if there was one) as part of the results. Defaults to false.
 
Defaults are either noted or taken from algorithm values, which themselves may have defaults. This allows very simple queries for the simple, most used cases.
 
The query returns personalized recommendations, similar items, or a mix including backfill. The query itself determines this by supplying item, user, both, or neither. Some examples are:

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
When setting an available date and expire date on **items** you will set the name of the fields to be used in the engine.json `expireDateName` and `availableDateName` params, the current date can be used as a filter, the UR will check that the current date is before the expire date, and after or equal to the available date. If the above fields are defined in engne.json a date must accompany any query since all items are assumed to have this property. When setting these values for item propoerties both must be specified so if a one-sided query is desires set the available date to some time in the past and/or the expire date to sometime far in the future, this guarantees that the item will not be filtered out from one or the other limit. If the available and expire fields are named in the engine.json then the date can be passed in with the query or, if it is absent the current PredictionServer date will be used. 

**Note:** a somewhat hidden effect of this is that if these fields are specified in the engine.json the current date type range filter will apply to **every query made**. It is an easy modification to only apply them to queries that contain the `currentDate` as shown below so ask us if you need it.

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

This returns only popular items. All returned scores will be 0 but the order will be based on relative popularity. Property-based biases for boosts and filters can also be applied as with any other query. See `algorithm: backfillField:` parameters for a discussion of how the ranking of all items is calculated.

##Events
The Universal takes in potentially many events. These should be seen as a primary event, which is a very clear indication of a user preference and secondary events that we think may tell us something about user "taste" in some way. The Universal Recommender is built on a distributed Correlated Cross-Occurrence (CCO) Engine, which basically means that it will test every secondary event to make sure that it actually corelates to the primary one and those that do not correlate will have little or no effect on recommendations (though they will make it longer to train and get query results). See ActionML's Analysis Tools for methods to test event predictiveness.

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
			"category": ["electronics", "mobile-phones"],
			"expireDate": "2016-10-05T21:02:49.228Z"
		},
		"eventTime" : "2015-10-05T21:02:49.228Z"
	}

	{
		"event":"$set",
		"entityType":"item",
		"entityId":"tweek_",
		"properties": {
			"content-type":["tv_show"],
			"genres":["10751","16"],
			"actor":["1513229","89756","215384","120310"],
			"keywords":["409"],
			"first_air_at":["1960"]
		}
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

##More Links

 - For a step-by-step for setting up a cluster see this [guide](https://github.com/actionml/cluster-setup)
 - For Scaling and Architecture see [these docs](https://github.com/actionml/cluster-setup/blob/master/architecture-and-scaling.md)

## Version Changelog

### v0.3.0

 - **WARNING**: This version require PredictionIO v0.9.6 **from the ActionML repo here: [https://github.com/actionml/PredictionIO/tree/master](https://github.com/actionml/PredictionIO/tree/master)**
 - Now supports the `SelfCleanedDataSource` trait. Adding params to the `DataSource` part of `engine.json` allows control of de-duplication, property event compaction, and a time window of event. The time window is used to age out the oldest events. Note: this only works with the ActionML fork of PredictionIO found in the repo mentioned above.
 - changed `backfillField: duration` to accept Scala Duration strings. This will require changes to all engine.json files that were using the older # of seconds duration.
 - added support for indicator predictiveness testing with the MAP@k tool
 - fixed a bug which requires that in the engine.json the `typeName` is required to be `"items"`, with this release the type can be more descriptive.

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