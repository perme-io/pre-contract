# PRE-Contract
(ICON JAVA SCORE)
---

# Getting Started
## Requirements

1. **Openjdk 11**

2. **goloop CLI**
see. https://icondev.io/getting-started/how-to-write-a-smart-contract

~~~
$ git clone git@github.com:icon-project/goloop.git
$ GOLOOP_ROOT=/path/to/goloop
$ cd ${GOLOOP_ROOT}
$ make goloop
$ ./bin/goloop version

goloop version v1.2.6 ...  # Success if version information is displayed.
~~~

## Generate a keystore and get test ICX 

~~~
$ ${GOLOOP_ROOT}/bin/goloop ks gen --out keystore.json --password xxxx

hxd9d79a5695f...2459905 ==> keystore.json  # Success if hxADDR is displayed.
~~~

Faucets for test ICX: https://icondev.io/icon-stack/icon-networks/main-network#test-network-faucets


## Build

```shell
./gradlew build
./gradlew optimizedJar
```

## Test

```shell
./gradlew cleanTest test -i
```

## Deploy

Generate a keystore and get some ICX for deploy(see above.)

Following environment variables must be set before deploy

| Name                  | Description                                               |
|-----------------------|-----------------------------------------------------------|
| GOLOOP_RPC_URI        | URI for RPC Endpoint (ex: http://localhost:9080/api/v3)   |
| GOLOOP_RPC_NID        | Network ID of the endpoint                                |
| GOLOOP_RPC_KEY_STORE  | Keystore file for the wallet to send transaction          |
| GOLOOP_RPC_KEY_SECRET | Secret file containing the password for the keystore file |
| GOLOOP_RPC_KEY_PASS   | Password for the keystore file                            |

Deploy the optimized jar to the network
```shell
./gradlew deployToRpc
```

Sample output
```
> Task :buildSrc:compileJava NO-SOURCE
> Task :buildSrc:compileGroovy UP-TO-DATE
> Task :buildSrc:processResources NO-SOURCE
> Task :buildSrc:classes UP-TO-DATE
> Task :buildSrc:jar UP-TO-DATE
> Task :buildSrc:assemble UP-TO-DATE
> Task :buildSrc:compileTestJava NO-SOURCE
> Task :buildSrc:compileTestGroovy NO-SOURCE
> Task :buildSrc:processTestResources NO-SOURCE
> Task :buildSrc:testClasses UP-TO-DATE
> Task :buildSrc:test NO-SOURCE
> Task :buildSrc:check UP-TO-DATE
> Task :buildSrc:build UP-TO-DATE

> Task :java-score:deployToRpc
>>> deploy to http://localhost:9080/api/v3/icon_dex
>>> optimizedJar = /pre-contract/java-score/build/libs/java-score-0.9.0-optimized.jar
>>> keystore = /goloop/config/keystore.json
Succeeded to deploy: 0x4772c41d26e0f3f10d814506dc43d6478b8ae1fea25fa5ae59425fabefae302d
SCORE address: cx25e2769d4e8dcf2081854ae2b3e48f6ed18fdf50

BUILD SUCCESSFUL in 3s
3 actionable tasks: 1 executed, 2 up-to-date
```

Then you should set the environment variable, `PRE_CONTRACT`, as the address of the deployed contract.
For further commands. For the previous sample output, it may use the following for it.

```shell
export PRE_CONTRACT=cx25e2769d4e8dcf2081854ae2b3e48f6ed18fdf50
```

Then you can check the list of nodes with the following command.

```shell
./gradlew allNode
```

## Update

To update the contract with the new code, use the following command.

```shell
./gradlew deployToRpc -PdeployTarget=<contract address>
```
