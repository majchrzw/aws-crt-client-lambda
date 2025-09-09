# AWS CRT client in native lambda
Adding aws crt client as dependency causes test clients to fail in integration tests.

Branch `netty-client` have default netty-nio client used and in that case all tests are working.

Branch `aws-crt-client` is using aws-crt client for sending sns messages. 
In this case test client cannot connect to localstack in integration test.

It is worth mentioning that in both cases test clients are based on url-connection-client

To build see both example run command:
```shell
./gradlew quarkusIntTest --stacktrace -Dquarkus.package.jar.enabled=false  -Dquarkus.native.enabled=true
```