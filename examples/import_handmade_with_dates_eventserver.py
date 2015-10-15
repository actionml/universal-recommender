"""
Import sample data for recommendation engine
"""

import predictionio
import argparse
import random
import datetime
import pytz
import dateutil.parser

RATE_ACTIONS_DELIMITER = ","
SEED = 1


def import_events(client, file):
  random.seed(SEED)
  count = 0

  # year, month, day[, hour[, minute[, second[
  #event_date = datetime.datetime(2015, 8, 13, 12, 24, 41)
  # These date values are chosen to match the multi-query-handmade.sh queries for testing purposes
  num_samples = 50
  num_usage_event_lines = 17
  start_date = dateutil.parser.parse("2015-08-12T11:28:45.114-07:00")
  item_date = start_date
  end_date = dateutil.parser.parse("2015-10-01T11:28:45.114-07:00")
  date_increment = (end_date - start_date) / (num_usage_event_lines * num_samples)
  event_date = item_date
  print "Date increment: " + str(date_increment)
  available_date = item_date + datetime.timedelta(days=-2)
  expire_date = item_date + datetime.timedelta(days=2)
  end = event_date.isoformat()
  start = event_date.isoformat()

  #date_increment = datetime.timedelta(minutes=1)
  #item_date = datetime.datetime.now(pytz.timezone("America/New_York"))
  # event_date = datetime.now(pytz.utc)
  print "Importing data."

  for it in range(1, num_samples, 1):
    f = open(file, 'r')
    for line in f:
      data = line.rstrip('\r\n').split(RATE_ACTIONS_DELIMITER)
      # For demonstration purpose action names are taken from input along with secondary actions on
      # For the UR add some item metadata
      user = "u" + str(random.randint(0, 1000))
      if (data[1] == "purchase"):
        client.create_event(
          event=data[1],
          entity_type="user",
          entity_id=data[0],
          target_entity_type="item",
          target_entity_id=data[2],
          event_time=event_date
        )
        print "Event: " + data[1] + " user: " + data[0] + " target_entity_id: " + data[2] + " eventDate: " + event_date.isoformat()
      elif (data[1] == "view"):  # assumes other event type is 'view'
        client.create_event(
          event=data[1],
          entity_type="user",
          entity_id=data[0],
          target_entity_type="item",  # type of item in this action
          target_entity_id=data[2],
          event_time=event_date
        )
        print "Event: " + data[1] + " user: " + data[0] + " target_entity_id: " + data[2] + " eventDate: " + event_date.isoformat()
      event_date += date_increment

  f.close()

  #now set some properties once per items with various dates
  num_items = 5
  start_date = dateutil.parser.parse("2015-08-12T11:28:45.114-07:00")
  item_date = start_date
  end_date = dateutil.parser.parse("2015-10-01T11:28:45.114-07:00")
  date_increment = (end_date - start_date)/num_items
  print "Date increment: " + str(date_increment)
  event_date = item_date
  available_date = item_date + datetime.timedelta(days=-2)
  expire_date = item_date + datetime.timedelta(days=2)
  end = event_date.isoformat()
  start = event_date.isoformat()


  f = open(file, 'r')
  count = 0
  for line in f:
    data = line.rstrip('\r\n').split(RATE_ACTIONS_DELIMITER)
    if (data[1] == "$set"):  # must be a set event
      date_choice = count % 3
      print "data_choice = " + str(date_choice)
      if (date_choice == 2): # both bounds and date for daterange
        client.create_event(
          event=data[1],
          entity_type="item",
          entity_id=data[0],
          properties={"category": [data[2]], "expiredate": expire_date.isoformat(),
                      "availabledate": available_date.isoformat(), "date": item_date.isoformat()},
          event_time=event_date
        )
        print "Event: " + data[1] + " item: " + data[0] + " properties/catagory: " + data[2] + " eventDate: " + \
              event_date.isoformat() + \
              " properties/availabledate: " + available_date.isoformat() + \
              " properties/date: " + item_date.isoformat() + \
              " properties/expiredate: " + expire_date.isoformat()
      elif (date_choice == 1): # available bound and date for daterange
        client.create_event(
          event=data[1],
          entity_type="item",
          entity_id=data[0],
          properties={"category": [data[2]],
                      "availabledate": available_date.isoformat(), "date": item_date.isoformat()},
          event_time=event_date
        )
        print "Event: " + data[1] + " item: " + data[0] + " properties/catagory: " + data[2] + " eventDate: " + \
              event_date.isoformat() + \
              " properties/availabledate: " + available_date.isoformat() + \
              " properties/date: " + item_date.isoformat()
      else: # expire bound and date for daterange
        client.create_event(
          event=data[1],
          entity_type="item",
          entity_id=data[0],
          properties={"category": [data[2]], "expiredate": expire_date.isoformat(),
                      "date": item_date.isoformat()},
          event_time=event_date
        )
        print "Event: " + data[1] + " item: " + data[0] + " properties/catagory: " + data[2] + " eventDate: " + \
              event_date.isoformat() + \
              " properties/date: " + item_date.isoformat() + \
              " properties/expiredate: " + expire_date.isoformat()
      count += 1
      expire_date += date_increment
      item_date += date_increment
      event_date += date_increment
      available_date += date_increment
  f.close()
  print "%s events are imported." % count
  print "Start at " + start + " end at " + end + " with increment in seconds of " + str(date_increment.seconds)


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
