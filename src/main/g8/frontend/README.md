# lora-transformer-ui/frontend

This is the directory for your web application. All web assets are expected to reside in the `dist` folder and
are required by the `backend` project. If you change the name of the folder then you must also affect the change
in just one place of the `backend/pom.xml` file.

## Frontend API usage

There are three types of API available:

* authentication - permits the user to login
* end device events - sensor meta data including position, naming, removal and more
* observations - actual sensor readings

As we don't prescribe the use of a particular frontend technology--there are so many to choose from!--the following sections
show the APIs that a frontend will need. Authentication is required when an "HTTP Unauthorized" response (a `403`) is replied
to the `/api` endpoints of end device events and observations.

### Authenticate

```bash
curl \
  -v \
  -d'{"username":"admin","password":"password"}' \
  -H"Content-Type: application/json" \
  http://localhost:9000/api/login
```

Note the token that is replied. Let's assume it is `c527c50b678dece80cbc1c7d` for the following commands.

### Subscribe to end device events

```bash
curl \
  -v \
  -H"Authorization: Bearer c527c50b678dece80cbc1c7d" \
  http://localhost:9000/api/end-devices
```

A front end is typically interested in the following end device events:

### SSE type: `PositionUpdated`

```json
{
  "nwkAddr": 1,
  "time": "1970-01-01T00:00:00Z",
  "position": {
    "lat": 1,
    "lng": 2,
    "alt": 3
  },
  "type": "PositionUpdated"
}
```

and

### SSE type: `NwkAddrRemoved`

```json
{"nwkAddr":1,"type":"NwkAddrRemoved"}
```

### Subscribe to observations

```bash
curl \
  -v \
  -H"Authorization: Bearer c527c50b678dece80cbc1c7d" \
  http://localhost:9000/api/$deviceType;format="norm"$
```

The format of the data will depend on your sensor.