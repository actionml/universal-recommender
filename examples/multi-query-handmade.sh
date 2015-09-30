#!/usr/bin/env bash

echo "These queries are meant to be run on data created with "
echo "'python examples/import_handmade_eventserver.py --accesskey <your-access-key>'"
echo "If run on other data the comments about what to expect may not apply, especailly for date range queries"

echo ""
echo "Recommendations for user: u1. galxy, nexus, surface"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u2. iphone, ipad, surface"
curl -H "Content-Type: application/json" -d '
{
    "user": "u2"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u3. galaxy, ipad, iphone, nexus"
curl -H "Content-Type: application/json" -d '
{
    "user": "u3"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u4. ipad, nexus, surface"
curl -H "Content-Type: application/json" -d '
{
    "user": "u4"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u5. galaxy, iphone, nexus, surface, ipad"
curl -H "Content-Type: application/json" -d '
{
    "user": "u5"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for anonymous user: u10, all from popularity. iphone, nexus, galaxy, surface, ipad"
curl -H "Content-Type: application/json" -d '
{
    "user": "u10"
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
echo "Recommendations for user: u1 with a tablets boost, blacklist 'nexus', should get only surface, galaxy"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": 2
    }],
    "blacklistItems": ["nexus"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones boost, blacklist 'galaxy', should only get tablets"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": 2
    }],
    "blacklistItems": ["galaxy"]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets filter, blacklist 'surface', should get only nexus"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -2
    }],
    "blacklistItems": ["surface"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones filter, blacklist 'galaxy', will get nothing since query is over-constrained"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": -2
    }],
    "blacklistItems": ["galaxy"]


}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1, blacklist 'galaxy', should get nexus, surface"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "blacklistItems": ["nexus"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for item: surface, blacklist 'ipad', should get galaxy, iphone, nexus"
curl -H "Content-Type: application/json" -d '
{
    "item": "surface",
    "num": 5,
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
echo "Warning: for the date range queries to work you must substitute dates that fit in the range when the events were imported and this is fixed in examples/import_handmade_eventserver.py"
echo ""
echo "Recommendations for user: u1, no date range"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1 after 8-10 so galaxy, nexus, surface"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "dateRange":{
        "name": "date",
        "after": "2015-08-10T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1 before 10-01 so galaxy, nexus, surface"
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
echo "Recommendations for user: u1, before 10/1 and after 8/10, galaxy, nexus, surface"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "dateRange":{
        "name": "date",
        "before": "2015-10-01T11:28:45.114-07:00"
        "after": "2015-08-10T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1, 'tablets' filter, before 10/1 and after 8/10"
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
        "before": "2015-10-01T11:28:45.114-07:00"
        "after": "2015-08-10T11:28:45.114-07:00"
    }
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u3, 'phones' boost, before 10/1 and after 8/10"
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
        "before": "2015-10-01T11:28:45.114-07:00"
        "after": "2015-08-10T11:28:45.114-07:00"
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
echo "Recommendations for user: u1, using currentDate type filter cannot work unless both avaiableDate and expireDate are set so will return nexus only"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 10,
    "currentDate": "2015-08-12T12:24:41-07:00"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recs that rely on popularity since the user has no usage data"
echo ""
echo ""

echo ""
echo "Recommendations for non-existant user: u10, all from popularity"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "num": 10
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for non-existant user: u10, all from popularity, tablets boost"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "num": 10,
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": 10
    }]
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for non-existant user: u10, all from popularity, tablets filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "num": 10,
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for non-existant user: u10, all from popularity, tablets filter and date filter, should be nexus only"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "num": 10,
    "currentDate": "2015-08-11T11:28:45.114-07:00",
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""


