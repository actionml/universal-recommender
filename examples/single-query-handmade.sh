#!/usr/bin/env bash

echo ""
echo "Queries illustrate use of date filters but must be adjusted to the current date to work properly"
echo ""

echo ""
echo "Recommendations for user: u1"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""
#2015-10-22T10:58:07.708-07:00

echo ""
echo "Recommendations for non-existant user: u10, all from popularity, tablets filter and date filter, will be empty unless you change the date to match when it was imported"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "currentDate": "2015-10-28T10:58:07.708-07:00",
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: u3, 'phones' boost, before 10/26 and after 10/22"
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "dateRange":{
        "name": "date",
        "before": "2015-10-26T10:58:07.708-07:00"
        "after": "2015-10-22T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""


