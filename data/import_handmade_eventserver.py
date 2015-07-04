"""
Import sample data for recommendation engine
"""

import predictionio
import argparse
import random

RATE_ACTIONS_DELIMITER = ","
SEED = 3

def import_events(client, file):
  f = open(file, 'r')
  random.seed(SEED)
  count = 0
  print "Importing data..."
  for line in f:
    data = line.rstrip('\r\n').split(RATE_ACTIONS_DELIMITER)
    # For demonstration purpose action names are taken from input along with secondary actions on
    # For the MMR add some item metadata

    if (data[1] == "purchase"):
      client.create_event(
        event=data[1],
        entity_type="user",
        entity_id=data[0],
        target_entity_type="item",
        target_entity_id=data[2],
      )
      print "Event: " + data[1] + " entity_id: " + data[0] + " target_entity_id: " + data[2]
    elif (data[1] == "view"): # assumes other event type is 'view'
      client.create_event(
        event=data[1],
        entity_type="user",
        entity_id=data[0],
        target_entity_type="item", # type of item in this action
        target_entity_id=data[2],
      )
      print "Event: " + data[1] + " entity_id: " + data[0] + " target_entity_id: " + data[2]
    elif (data[1] == "$set"): # must be a set event
      client.create_event(
        event=data[1],
        entity_type="item",
        entity_id=data[0],
        properties= { "category": [data[2]] }
      )
      print "Event: " + data[1] + " entity_id: " + data[0] + " properties/catagory: " + data[2]
    count += 1
  f.close()
  print "%s events are imported." % count

if __name__ == '__main__':
  parser = argparse.ArgumentParser(
    description="Import sample data for recommendation engine")
  parser.add_argument('--access_key', default='invald_access_key')
  parser.add_argument('--url', default="http://localhost:7070")
  parser.add_argument('--file', default="./data/sample-handmade-data.txt")

  args = parser.parse_args()
  print args

  client = predictionio.EventClient(
    access_key=args.access_key,
    url=args.url,
    threads=5,
    qsize=500)
  import_events(client, args.file)
