"""
Import sample data for the ur recommendation engine
shopping-cart recs
"""

import predictionio
import argparse
import random
import datetime
import pytz

ACTIONS_DELIMITER = ","
SEED = 1


def import_events(client, file):
  f = open(file, 'r')
  random.seed(SEED)
  count = 0
  now_date = datetime.datetime.now(pytz.utc) # - datetime.timedelta(days=2.7)
  current_date = now_date
  event_time_increment = datetime.timedelta(days= -0.8)
  print("Importing data...")

  for line in f:
    data = line.rstrip('\r\n').split(ACTIONS_DELIMITER)
    # For demonstration purpose action names are taken from input along with secondary actions on
    # For the UR add some item metadata

    if (data[1] == "purchase"):
      client.create_event(
        event=data[1],
        entity_type="user",
        entity_id=data[0],
        target_entity_type="item",
        target_entity_id=data[2],
        event_time = current_date
      )
      print("Event: " + data[1] + " entity_id: " + data[0] + " target_entity_id: " + data[2] + \
            " current_date: " + current_date.isoformat())
    count += 1
    current_date += event_time_increment

  f.close()
  print("%s events are imported." % count)


if __name__ == '__main__':
  parser = argparse.ArgumentParser(
    description="Import sample data for recommendation engine")
  parser.add_argument('--access_key', default='invald_access_key')
  parser.add_argument('--url', default="http://localhost:7070")
  parser.add_argument('--file', default="./data/sample-handmade-item-set-data.txt")

  args = parser.parse_args()
  print(args)

  client = predictionio.EventClient(
    access_key=args.access_key,
    url=args.url,
    threads=5,
    qsize=500)
  import_events(client, args.file)
