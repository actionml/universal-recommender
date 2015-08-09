#!/usr/bin/env bash

echo ""
echo "Recommendations for user: u1"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u2"
curl -H "Content-Type: application/json" -d '
{
    "user": "u2"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u3"
curl -H "Content-Type: application/json" -d '
{
    "user": "u3"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u4"
curl -H "Content-Type: application/json" -d '
{
    "user": "u4"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u5"
curl -H "Content-Type: application/json" -d '
{
    "user": "u5"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets boost"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": 5
    }]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones boost"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": 2
    }]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets filter"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -2
    }]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones filter"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": -2
    }]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1, max of one rec requested but due to blacklisting, may see none"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 1
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for item: surface, should return results but with low scores"
curl -H "Content-Type: application/json" -d '
{
    "item": "surface",
    "num": 4
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u5 who would not get recs without multiple action types"
curl -H "Content-Type: application/json" -d '
{
    "user": "u5",
    "num": 4
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets boost, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": 2
    }],
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones boost, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": 2
    }],
    "blacklistItems": ["ipad"]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets filter, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -2
    }],
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones filter, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": -2
    }],
    "blacklistItems": ["ipad"]


}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for item: surface, should return none, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "item": "surface",
    "num": 4,
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u5 who would not get recs without multiple action types, blacklist 'iphone'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u5",
    "num": 4,
    "blacklistItems": ["iphone"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for item: galaxy"
curl -H "Content-Type: application/json" -d '
{
    "item": "galaxy"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1, 'galaxy' blacklisted"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "blacklistItems": ["galaxy"]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo ""
echo "Warning: for the date range queries to work you must substitude dates that fit in the range when the events were imported and this is fixed in examples/import_handmade_eventserver.py"
echo ""
echo "Recommendations for user: u1, no date range"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1 after 8-12 so all"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "dateRange":{
        "name": "date",
        "after": "2015-08-12T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1 before 10-01 so all"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "dateRange":{
        "name": "date",
        "before": "2015-10-01T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1, no filter, narrow date range before 8/31 and after 8/02"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "dateRange":{
        "name": "date",
        "before": "2015-08-31T11:28:45.114-07:00",
        "after": "2015-09-02T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1, 'tablets' filter, narrow date range before 8/31 and after 8/02"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -2
    }],
    "dateRange":{
        "name": "date",
        "before": "2015-08-31T11:28:45.114-07:00",
        "after": "2015-09-02T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u3, 'phones' boost, wide date range before 9/05 and after 8/13"
curl -H "Content-Type: application/json" -d '
{
    "user": "u3",
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": 2
    }],
    "dateRange":{
        "name": "date",
        "before": "2015-09-05T11:28:45.114-07:00",
        "after": "2015-08-13T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: u1 with no date range, should be: galaxy, nexus, surface"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 10
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1, should return nexus only. Note that items with a missing range in their properties will never be returned by this query"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 10,
    "currentDate": "2015-09-02T12:24:41-07:00"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1, should return nexus only. Note that items with a missing range in their properties will never be returned by this query"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 10,
    "currentDate": "2015-08-30T12:24:41-07:00"
}' http://localhost:8000/queries.json
echo ""



