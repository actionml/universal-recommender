# Universal Recommender

##Quick Start

 1. [Install the PredictionIO framework](https://docs.prediction.io/install/) **be sure to choose HBase and Elasticsearch** for storage. This template requires Elasticsearch.
 2. Make sure the PIO console and services are running, check with `pio status`
 3. [Install this template](https://docs.prediction.io/start/download/) **be sure to specify this template** with `pio template get PredictionIO/template-scala-parallel-universal-recommendation`
 
**To import and experiment with the simple example data**

1. Create a new app name, change `appName` in `engine.json`
2. Run `pio app new **your-new-app-name**`
4. Import sample events by running `python data/import_handmade.py --access_key **your-access-key**` where the key can be retrieved with `pio app list`
3. The engine.json file in the root directory of your new UR template is set up for the data you just imported (make sure to create a new one for your data) Edit this file and change the `appName` parameter to match what you called the app in step #2
5. Perform `pio build`, `pio train`, and `pio deploy`
6. To execute some sample queries run `./examples/query-handmade.sh`

If there are timeouts, enable the delays that are commented out in the script&mdash;for now. In the production environment the engines will "warm up" with caching and will execute queries much faster. Also all services can be configured or scaled to meet virtually any performance needs.

**See the [Github README.md](https://github.com/PredictionIO/template-scala-parallel-universal-recommendation) for further usage instructions**

##What is a Universal Recommender

The Universal Recommender (UR) will accept a range of data, auto correlate it, and allow for very flexible queries. The UR is different from most recommenders in these ways:

* It takes a single very strong "primary" event type&mdash;one that clearly reflects a user's preference&mdash;and correlates any number of other event types to the primary event. This has the effect of using virtually any user action to recommend the primary action. Much of a user’s clickstream can be used to make recommendations. If a user has no history of the primary action (purchase for instance) but does have history of views, personalized recommendations for purchases can still be made. With user purchase history the recommendations become better. ALS-type recommenders have been used with event weights but except for ratings these often do not result in better performance.
* It can boost and filter based on events or item metadata/properties. This means it can give personalized recs that are biased toward “SciFi” and filtered to only include “Promoted” items when the business rules call for this.
* It can use a user's context to make recommendations even when the user is new. If usage data has been gathered for other users for referring URL, device type, or location, for instance, there may be a correlation between this data and items preferred. The UR can detect this **if** it exists and recommend based on this context, even to new users. We call this "micro-segmented" recommendations since they are not personal but group users based on limited contextual information. These will not be as good as when more is know about the user but may be better than simply returning popular items.
* It includes a fallback to some form of item popularity when there is no other information known about the user (not implemented in v0.1.0).
* All of the above can be mixed into a single query for blended results and so the query can be tuned to a great many applications. Also since only one query is made and boosting is supported, a query can be constructed with several fallbacks. Usage data is most important so boost that high, micro-segemnting data may be better than popularity so boost that lower, and popularity fills in if no other recommendations are available.

Other features:

 * Makes recommendations based on realtime user history. Even anonymous users will get recommendations if they have recorded preference history and a user-id. There is no hard requirement to retrain the model to make this happen. 
 
TBD:

 * Date range filters based on Date properties of items
 * Populatiy type recommendations backfill for returning "trending", or "hot" items when no other recommendations are available from the training data. 
 * Content-based correlators for content-based recommendations

## References

 * Other documentation of the algorithm is [here](http://mahout.apache.org/users/algorithms/intro-cooccurrence-spark.html)
 * A free ebook, which talks about the general idea: [Practical Machine Learning](https://www.mapr.com/practical-machine-learning).
 * A slide deck, which talks about mixing actions and other indicator types, including content-based ones: [Creating a Unified Recommender](http://www.slideshare.net/pferrel/unified-recommender-39986309?ref=http://occamsmachete.com/ml/)
 * Two blog posts: What's New in Recommenders: part [#1](http://occamsmachete.com/ml/2014/08/11/mahout-on-spark-whats-new-in-recommenders/) [#2](http://occamsmachete.com/ml/2014/09/09/mahout-on-spark-whats-new-in-recommenders-part-2/)
 * A post describing the log-likelihood ratio: [Surprise and Coincidence](http://tdunning.blogspot.com/2008/03/surprise-and-coincidence.html) LLR is used to reduce noise in the data while keeping the calculations O(n) complexity.
