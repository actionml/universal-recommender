"""
Import sample data for recommendation engine
"""

import predictionio
import argparse
import random
import datetime
import pytz

RATE_ACTIONS_DELIMITER = ","
PROPERTIES_DELIMITER = ":"
SEED = 1


def import_events(client, file):
  f = open(file, 'r')
  random.seed(SEED)
  count = 0
  # year, month, day[, hour[, minute[, second[
  #event_date = datetime.datetime(2015, 8, 13, 12, 24, 41)
  now_date = datetime.datetime.now(pytz.utc) # - datetime.timedelta(days=2.7)
  current_date = now_date
  event_time_increment = datetime.timedelta(days= -0.8)
  available_date_increment = datetime.timedelta(days= 0.8)
  event_date = now_date - datetime.timedelta(days= 2.4)
  available_date = event_date + datetime.timedelta(days=-2)
  expire_date = event_date + datetime.timedelta(days=2)
  print "Importing data..."

  items = set()
  for line in f:
    data = line.rstrip('\r\n').split(RATE_ACTIONS_DELIMITER)
    # For demonstration purpose action names are taken from input along with secondary actions on
    # For the UR add some item metadata

    action = data[1]
    if action in ('$set', '$unset', '$delete'):
        item_id = data[0]
        items.add(item_id)
        properties = data[2].split(PROPERTIES_DELIMITER)
        prop_name = properties.pop(0)
        prop_value = properties if not prop_name == 'defaultRank' else float(
            properties[0])
        client.create_event(
            event=action,
            entity_type="item",
            entity_id=item_id,
            event_time=current_date,
            properties={prop_name: prop_value}
        )
        print(
            'Event: {0} entity_id: {1} properties/{2}: {3} current_date: {4}'.format(
                action, item_id, prop_name, str(prop_value),current_date.isoformat()))

    else:
        user_id = data[0]
        item_id = data[2]
        client.create_event(
            event=action,
            entity_type="user",
            entity_id=user_id,
            target_entity_type="item",
            target_entity_id=item_id,
            event_time=current_date
        )
        print(
            'Event: {0} entity_id: {1} target_entity_id: {2} current_date: {3}'
            .format(action, item_id, item_id, current_date.isoformat()))
    count += 1
    current_date += event_time_increment

  print "All items: " + str(items)
  for item in items:

    client.create_event(
      event="$set",
      entity_type="item",
      entity_id=item,
      properties={"expires": expire_date.isoformat(),
                  "available": available_date.isoformat(),
                  "date": event_date.isoformat()}
    )
    print "Event: $set entity_id: " + item + \
            " properties/availableDate: " + available_date.isoformat() + \
            " properties/date: " + event_date.isoformat() + \
            " properties/expireDate: " + expire_date.isoformat()
    expire_date += available_date_increment
    event_date += available_date_increment
    available_date += available_date_increment
    count += 1

  f.close()
  print "%s events are imported." % count


if __name__ == '__main__':
  parser = argparse.ArgumentParser(
    description="Import sample data for recommendation engine")
  parser.add_argument('--access_key', default='123456789')
  parser.add_argument('--url', default="http://localhost:7070")
  parser.add_argument('--file', default="./data/sample-rank-data.txt")

  args = parser.parse_args()
  print args

  client = predictionio.EventClient(
    access_key=args.access_key,
    url=args.url,
    threads=5,
    qsize=500)
  import_events(client, args.file)
