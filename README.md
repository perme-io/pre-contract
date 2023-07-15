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

~~~
$ gradle build
$ gradle optimizedJar
~~~


## Test

~~~
$ gradle cleanTest test -i
~~~


## Deploy

Generate a keystore and get test ICX (see above.)

Deploy the optimized jar
~~~
${GOLOOP_ROOT}/bin/goloop rpc sendtx deploy ./java-score/build/libs/java-score-0.9.0-optimized.jar --uri https://lisbon.net.solidwallet.io/api/v3 --key_store keystore.json --key_password xxxx --nid 2 --step_limit 10000000000 --content_type application/java
 
"0xe33fb39f737b4698b3d93...1b73a9831f2786bdf3"  # Success if tx hash(0xADDR) is displayed.
~~~

Update Score
~~~
${GOLOOP_ROOT}/bin/goloop rpc sendtx deploy ./java-score/build/libs/java-score-0.9.0-optimized.jar --uri https://lisbon.net.solidwallet.io/api/v3 --key_store keystore.json --key_password xxxx --nid 2 --step_limit 10000000000 --content_type application/java --to cxf3...f57
${GOLOOP_ROOT}/bin/goloop rpc txresult 0x31...fdb --uri https://lisbon.net.solidwallet.io/api/v3
~~~
