#!/usr/bin/env bash

echo ""
echo "Queries to illustrate many use cases on a small standard dataset and for an automated integration test."
echo ""
echo "WARNING: for this to produce the correct result you must:"
echo "  1. Import data with"
echo "     $ python3 examples/import_handmade.py --access_key <your-app-accesskey>"
echo "  2. Copy handmade-engine.json to engine.json."
echo "  3. Run 'pio build', 'pio train', and 'pio deploy'"
echo "  4. The queries must be run the same day as the import was done because date filters are part of the test."
echo "NOTE: due to available and expire dates you should never see the Iphone 5 or Iphone 6."

echo ""
echo "============ simple item-set recs ============"
echo ""
echo "Recommendations for user: iPhone 6"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "itemSet": ["iPhone 6"]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: iPhone 7"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "itemSet": ["iPhone 7"]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: Nexus 6p"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "itemSet": ["iPhone 6p"]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: AirPods"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "itemSet": ["AirPods"]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: USB type-C cable"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "itemSet": ["USB type-C cable"]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: iPhone 6 charging cradle"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "itemSet": ["iPhone 6 charging cradle"]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for user: iPhone earbuds and iPhone 6 case"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "itemSet": ["iPhone earbuds", "iPhone 6 case"]
}' http://localhost:8000/queries.json
echo ""


