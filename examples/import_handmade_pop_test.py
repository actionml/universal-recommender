"""
Import sample data for recommendation engine
"""

import predictionio
import argparse
import random
import datetime
import pytz
from tzlocal import get_localzone

RATE_ACTIONS_DELIMITER = ","
PROPERTIES_DELIMITER = ":"
SEED = 1
local_tz = get_localzone()

def import_events(client, file, days_offset):
  f = open(file, 'r')
  random.seed(SEED)
  count = 0
  event_date = datetime.datetime.now(tz=local_tz) + datetime.timedelta(days=days_offset)
  print("Importing data...")

  for line in f:
    data = line.rstrip('\r\n').split(RATE_ACTIONS_DELIMITER)
    # For demonstration purpose action names are taken from input along with secondary actions on
    # For the UR add some item metadata

    if (data[1] == "purchase"):
      client.create_event(
        event=data[1],
        entity_type="user",
        entity_id=data[0],
        target_entity_type="item",
        target_entity_id=data[2],
        event_time=event_date
      )
      print("Event: " + data[1] + " user: " + data[0] + " item: " + data[2] + " date: " + str(event_date))
    elif (data[1] == "view"):  # assumes other event type is 'view'
      client.create_event(
        event=data[1],
        entity_type="user",
        entity_id=data[0],
        target_entity_type="item",  # type of item in this action
        target_entity_id=data[2],
        event_time=event_date
      )
      print("Event: " + data[1] + " user: " + data[0] + " item: " + data[2] + " date: " + str(event_date))
    count += 1
  f.close()
  print("%s events are imported." % count)


if __name__ == '__main__':
  parser = argparse.ArgumentParser(
    description="Import sample data for recommendation engine")
  parser.add_argument('--access_key', default='invald_access_key')
  parser.add_argument('--url', default="http://localhost:7070")
  parser.add_argument('--file1', default="./data/sample-handmade-data1.txt")
  parser.add_argument('--file2', default="./data/sample-handmade-data2.txt")
  parser.add_argument('--file3', default="./data/sample-handmade-data3.txt")

  args = parser.parse_args()
  print(args)

  client = predictionio.EventClient(
    access_key=args.access_key,
    url=args.url,
    threads=5,
    qsize=500)
  # this is to spread events around two time periods, now->3 days ago, and 4 days ago to 6 days ago
  # popular, trending, and hot over the a 3-day period ending on offset_days, which would be the most recent,
  # the duration of the actual pop-model calc is in the engin.json so these dates work with some multiple of a
  # day for that value (expressed in seconds). This allows us to test the pop-model as well as the "offsetDate"
  # in the prams for training. The pop-model queries should have the same results for both timespans if the
  # "offsetDate" is now, and now - 4days
  import_events(client, args.file1, 0)# last 3 days
  import_events(client, args.file2, -1)
  import_events(client, args.file3, -2)# first batch ends 2 days in the past
  import_events(client, args.file1, -4)# starting 4 days in the past, so skips 2 days for tests of the offset date
  import_events(client, args.file2, -5)
  import_events(client, args.file3, -6)
