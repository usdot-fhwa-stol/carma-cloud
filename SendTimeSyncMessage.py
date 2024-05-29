import http.client
import json
import requests

connection = http.client.HTTPConnection("localhost:8080")
# headers = {'Content-type': 'application/json'}
foo = {'timestep': 100, 'seq': 1}

connection.request("POST", "/carmacloud/simulation/dumb", json.dumps(foo))
x = requests.post("http://127.0.0.1:8080/carmacloud/simulation/dumb", json.dumps(foo))
print(x)

response = connection.getresponse()
print(response.read().decode())

connection.request("GET", '/api/rop/details')
response = connection.getresponse()
print(response.read().decode())