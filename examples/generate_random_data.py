import numpy as np
import json
import sys

scale = sys.argv[1]

if scale=='small':
    N_events = 1000 # Number of purchase events
    l_item = 0.09 # p parameter geometric distribution, determines item density
    l_user = 0.04 # p parameter geometric distribution, determines user density
elif scale=='medium':
    N_events = 300000
    l_item = 0.006
    l_user = 0.00005
elif scale=='large':
    N_events = 6000000
    l_item = 0.00006
    l_user = 0.0000008
else:
    raise ValueError("Please provide scale (small / medium / large)")


outfile_pur = 'data/input_data_purchase.json'
outfile_vw = 'data/input_data_view.json'

# Create purchase events
N_e = N_events
l_i = l_item # 100000
l_u = l_user # 3000000

pio_event = {"eventTime": "2017-05-02T00:00:00.000-00:00",
             "entityType": "user",
             "targetEntityType": "item",
             "event": "purchase",
             "entityId": "example",
             "targetEntityId": "example",
             "properties": {}}

event_name = 'purchase'
i = np.random.geometric(l_i, N_e)
u = np.random.geometric(l_u, N_e)

print('Purchase')
print('Unique items: %d' % np.unique(i).shape[0])
print('Unique users: %d' % np.unique(u).shape[0])
print('Events: %d' % N_e)

purchase_events = zip(i, u)


with open(outfile_pur, 'w') as f:
    for item, user in purchase_events:
        
        pio_event['entityId'] = str(user)
        pio_event['targetEntityId'] = str(item)
        pio_event['event'] = event_name
        
        f.write(json.dumps(pio_event) + '\n')

print('Stored in %s' % outfile_pur)

# Create view events
N_e = int(1.4 * N_events)
l_item = l_i / 0.9 # 100000
l_user = l_u / 0.9 # 3000000

event_name = 'view'
i = np.random.geometric(l_item, N_e)
u = np.random.geometric(l_user, N_e)

print('View')
print('Unique items: %d' % np.unique(i).shape[0])
print('Unique users: %d' % np.unique(u).shape[0])
print('Events: %d' % N_e)

view_events = zip(i, u)


with open(outfile_vw, 'w') as f:
    for item, user in view_events:
        
        pio_event['entityId'] = str(user)
        pio_event['targetEntityId'] = str(item)
        pio_event['event'] = event_name
        
        f.write(json.dumps(pio_event) + '\n')

print('Stored in %s' % outfile_vw)