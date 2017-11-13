echo ""
echo "============ Advanced business rules ============"
echo ""


echo "Recommendations for user: u-3"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u-3"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u-3, Tablets filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u-3",
    "fields": [{
        "name": "categories",
        "values": ["Tablets"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u-3, Tablets boost"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u-3",
    "fields": [{
        "name": "categories",
        "values": ["Tablets"],
        "bias": 20
    }]
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u-3, Tablets excluded"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u-3",
    "fields": [{
        "name": "categories",
        "values": ["Tablets"],
        "bias": 0
    }]
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u-3, Tablets excluded, Estados Unidos Mexicanos boosted"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u-3",
    "fields": [{
        "name": "categories",
        "values": ["Tablets"],
        "bias": 0
    }, {
        "name": "countries",
        "values": ["Estados Unidos Mexicanos"],
        "bias": 5
    }]
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u-3, Tablets and Samsung excluded, Estados Unidos Mexicanos boosted, phones included"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u-3",
    "fields": [{
        "name": "categories",
        "values": ["Tablets", "Samsung"],
        "bias": 0
    },{
        "name": "categories",
        "values": ["Phones"],
        "bias": -1
    }, {
        "name": "countries",
        "values": ["Estados Unidos Mexicanos"],
        "bias": 5
    }]
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u-3, Tablets and Samsung boosted, Estados Unidos Mexicanos excluded, phones included"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u-3",
    "fields": [{
        "name": "categories",
        "values": ["Tablets", "Samsung"],
        "bias": 5
    },{
        "name": "categories",
        "values": ["Phones"],
        "bias": -1
    }, {
        "name": "countries",
        "values": ["Estados Unidos Mexicanos"],
        "bias": 0
    }]
}' http://localhost:8000/queries.json
echo ""

