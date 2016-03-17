#!/usr/bin/env bash

echo ""
echo "Queries to illustrate many use cases on a small standard dataset and for an automated integration test."
echo ""
echo "WARNING: for this to produce the correct result you must:"
 echo"  1. Import data with "
echo "     $ python examples/import_handmade.py --access_key <your-app-accesskey>"
echo "  2. Copy handmade-engine.json to engine.json."
echo "  3. Run 'pio build', 'pio train', and 'pio deploy'"
echo "  4. The queries must be run the same day as the import was done because date filters are part of the test."
echo "NOTE: due to available and expire dates you should never see the Iphone 5 or Iphone 6."

echo ""
echo "============ simple user recs ============"
echo ""
echo "Recommendations for user: u1"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: U 2"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "U 2"
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: u-3"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u-3"
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: u-4"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: u5"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u5"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "============ simple similar item recs ============"
echo ""
echo "Recommendations for item: Iphone 4"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "Iphone 4"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: Ipad-retina"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "Ipad-retina"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: Nexus"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "Nexus"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: Galaxy"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "Galaxy"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: Surface"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "Surface"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "============ popular item recs only ============"
echo ""
echo "query with no item or user id, ordered by popularity"
echo ""
curl -H "Content-Type: application/json" -d '
{
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for non-existant user: xyz, all from popularity"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "xyz"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for non-existant item: xyz, all from popularity"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "xyz"
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for no user no item, all from popularity, Tablets filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "fields": [{
        "name": "categories",
        "values": ["Tablets"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for no user no item, all from popularity, Tablets boost"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "fields": [{
        "name": "categories",
        "values": ["Tablets"],
        "bias": 1.05
    }]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for no user no item, all from popularity, Tablets boost, Estados Unidos Mexicanos filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "fields": [{
        "name": "categories",
        "values": ["Tablets"],
        "bias": 1.05
    }, {
        "name": "countries",
        "values": ["Estados Unidos Mexicanos"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "============ dateRange filter ============"
echo ""
if [[ "$OSTYPE" == "linux-gnu" ]]; then
  BEFORE=`date --date="tomorrow" --iso-8601=seconds`
  AFTER=`date --date="1 day ago" --iso-8601=seconds`
else
  BEFORE=`date -v +1d +"%Y-%m-%dT%H:%M:%SZ"`
  AFTER=`date -v -1d +"%Y-%m-%dT%H:%M:%SZ"`
fi
#echo "before: $BEFORE after: $AFTER"
echo "Recommendations for user: u1"
echo ""
curl -H "Content-Type: application/json" -d "
{
    \"user\": \"u1\",
    \"dateRange\": {
        \"name\": \"date\",
        \"before\": \"$BEFORE\",
        \"after\": \"$AFTER\"
    }
}" http://localhost:8000/queries.json
echo ""

echo ""
echo "============ query with item and user *EXPERIMENTAL* ============"
# This is experimental, use at your own risk, not well founded in theory
echo ""
echo "Recommendations for no user no item, all from popularity, Tablets boost, Estados Unidos Mexicanos filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "item": "Iphone 4"
}' http://localhost:8000/queries.json
echo ""

