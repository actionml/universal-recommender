#!/usr/bin/env bash

echo ""
echo "Recommendations for user: u3 with no date range, should be:"
echo "{"itemScores":[{"item":"ipad","score":0.15998253226280212},{"item":"galaxy","score":0.12798602879047394},{"item":"iphone","score":0.050320517271757126},{"item":"nexus","score":0.050320517271757126}]}"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u3",
    "num": 10
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u3, should return nexus only since it is the only one not seen and in current date is between available and expired."
curl -H "Content-Type: application/json" -d '
{
    "user": "u3",
    "num": 10,
    "dateRange":{
        "name": "expiredate",
        "before": "2015-08-31T12:24:41-07:00",
        "after": "2015-09-02T12:24:41-07:00"
    }
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u3, should return nexus only since it is the only one not seen and in current date is between available and expired."
curl -H "Content-Type: application/json" -d '
{
    "user": "u3",
    "num": 10,
    "currentDate": "2015-08-30T12:24:41-07:00"
}' http://localhost:8000/queries.json
echo ""
