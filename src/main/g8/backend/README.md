# lora-transformer-ui/backend

For all running, debugging and testing, start the sandbox:

```
sandbox | docker-compose -p xdp-sandbox -f - up
```

...and then run as per any Java program. Given the use of the Streambed toolkit, your program will connect to the sandbox based services.

To package:

```
mvn package
mvn docker:build
```
