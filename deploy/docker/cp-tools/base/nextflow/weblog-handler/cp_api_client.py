
class CloudPipelineApi(object):

    def __init__(self, api, token):
        self.api = api
        self.token = token


    def send_engine_events(self, events):
        for e in events:
            print(e)