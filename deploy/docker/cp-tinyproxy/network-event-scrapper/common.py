import collections

HostAction = collections.namedtuple('HostAction', 'action,host_name,host_ip,run_id,phase')
Host = collections.namedtuple('Host', 'host_name,host_ip,run_id')
NetworkEvent = collections.namedtuple('NetworkEvent', 'reporter,timestamp,host_name,host_ip,run_id,resource,resource_host,method')