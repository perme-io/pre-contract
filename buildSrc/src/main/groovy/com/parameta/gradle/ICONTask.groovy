package com.parameta.gradle

import foundation.icon.icx.IconService
import foundation.icon.icx.TransactionBuilder
import foundation.icon.icx.data.Address
import foundation.icon.icx.data.Bytes
import foundation.icon.icx.data.TransactionResult
import foundation.icon.icx.transport.http.HttpProvider
import foundation.icon.icx.KeyWallet
import foundation.icon.icx.SignedTransaction
import foundation.icon.icx.Call
import foundation.icon.icx.Transaction
import foundation.icon.icx.transport.jsonrpc.RpcError
import foundation.icon.icx.transport.jsonrpc.RpcItem
import foundation.icon.icx.transport.jsonrpc.RpcValue
import foundation.icon.icx.transport.jsonrpc.RpcObject
import foundation.icon.icx.transport.monitor.Monitor
import org.gradle.api.DefaultTask
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option

import java.util.function.Function

abstract class ICONTask extends DefaultTask {
    @Input
    abstract Property<String> getRpcURI()

    @Input
    abstract Property<BigInteger> getNid()

    @Option(option="rpc", description = "RPC URI for the network (ex:https://localhost/api/v3/icon_dex)")
    abstract void setRpcURI(String uri)

    static Address asAddress(String s) {
        if (s == null) {
            return null
        }
        return new Address(s)
    }

    static BigInteger asBigInteger(String s) {
        if (s==null) {
            return null
        }
        if (s.startsWith("0x")) {
            return new BigInteger(s.substring(2), 16)
        } else {
            return new BigInteger(s, 10)
        }
    }

    static Integer asInteger(String s) {
        if (s==null) {
            return null
        }
        if (s.startsWith("0x")) {
            return Integer.valueOf(s.substring(2), 16)
        } else {
            return Integer.valueOf(s, 10)
        }
    }

    static String asRpcString(Object v) {
        var rv = asRpcItem(v)
        if (rv == null) {
            return null
        } else {
            return rv.asString()
        }
    }

    static RpcItem asRpcItem(String v) { v == null ? null : new RpcValue(v) }
    static RpcItem asRpcItem(Address v) { v == null ? null : new RpcValue(v) }
    static RpcItem asRpcItem(BigInteger v) { v == null ? null : new RpcValue(v) }
    static RpcItem asRpcItem(long v) { new RpcValue(BigInteger.valueOf(v)) }
    static RpcItem asRpcItem(Bytes v) { v == null ? null : new RpcValue(v) }
    static RpcItem asRpcItem(Boolean v) { v == null ? null : new RpcValue(v) }
    static RpcItem asRpcItem(RpcItem v) { v }
    static RpcItem asRpcItem(Object v) {
        if (v == null) {
            return RpcValue.NULL
        }
        if (v instanceof String) {
            return asRpcItem(v)
        } else if (v instanceof Address) {
            return asRpcItem(v)
        } else if (v instanceof BigInteger) {
            return asRpcItem(v)
        } else if (v instanceof Bytes) {
            return asRpcItem(v)
        } else if (v instanceof Boolean) {
            return asRpcItem(v)
        } else if (v instanceof Map) {
            return asRpcItem(v)
        } else if (v instanceof RpcItem) {
            return v
        }
        throw new IllegalArgumentException("UnknownType(${v.getClass()}) for RpcItem")
    }

    static<T> RpcObject asRpcItem(Map<String,T> params) {
        var paramsBuilder = new RpcObject.Builder()
        params.forEach {it, value -> {
            def v = asRpcItem(value)
            if (v != null && !v.isNull()) {
                paramsBuilder.put(it, asRpcItem(value))
            }
        }}
        return paramsBuilder.build()
    }


    @Option(option="nid", description = "NID of the network")
    void setNidOption(String v) {
        nid.set(asBigInteger(v))
    }

    ICONTask() {
        rpcURI.convention(System.getenv('GOLOOP_RPC_URI'))
        nid.convention(asBigInteger(System.getenv('GOLOOP_RPC_NID')))
    }

    private KeyWallet keyWallet

    @Input
    KeyWallet getWallet() {
        if (keyWallet == null) {
            var keystore = new File(System.getenv('GOLOOP_RPC_KEY_STORE'))
            String password
            var secret = System.getenv('GOLOOP_RPC_KEY_SECRET')
            if (secret!="") {
                password = new String((new File(secret)).readBytes())
            } else {
                password = System.getenv('GOLOOP_RPC_KEY_PASSWORD')
            }
            keyWallet = KeyWallet.load(password, keystore)
        }
        return keyWallet
    }

    private IconService service = null

    @Input
    IconService getIconService() {
        if (service == null) {
            service = new IconService(new HttpProvider(rpcURI.get()))
        }
        return service
    }


    private Bytes estimateAndSendTx(Transaction tx) {
        var svc = getIconService()
        var estimated = svc.estimateStep(tx).execute()
        var stepLimit = estimated.add(BigInteger.valueOf(10000))
        var signedTx = new SignedTransaction(tx, wallet, stepLimit)
        return svc.sendTransaction(signedTx).execute()
    }

    TransactionResult waitResult(Bytes txHash) {
        return waitResult(txHash, 5000)
    }

    TransactionResult waitResult(Bytes txHash, long limit) {
        final long SLEEP_UNIT = 1000

        var svc = getIconService()
        var remains = limit

        while (remains>=0) {
            try {
                var result = svc.getTransactionResult(txHash).execute()
                return result
            } catch (RpcError err) {
                logger.debug("RPC Failure: ${err}")
            }
            long delay = Math.min(SLEEP_UNIT, remains)
            delay = Math.max(delay, 1)
            Thread.sleep(delay)
            remains -= delay
        }
        return null
    }

    TransactionResult sendTransactionAndWait(Transaction tx) {
        var txHash = estimateAndSendTx(tx)
        return waitResult(txHash)
    }

    Bytes sendCall(Address contract, String method, Map<String, RpcItem> params) {
        return sendCall(contract, method, params, null)
    }

    /**
     * Send call transaction
     * @param contract Address of the contract to call
     * @param method Name of the method to call
     * @param params Parameters
     * @param value Amount of COIN to transfer on the transaction
     * @return hash of the transaction (used in @ref{#waitResult()})
     */
    Bytes sendCall(Address contract, String method, Map<String, RpcItem> params, BigInteger value) {
        var transaction = TransactionBuilder.newBuilder()
                .from(wallet.getAddress())
                .to(contract)
                .nid(nid.get())
                .value(value)
                .call(method)
                .params(asRpcItem(params))
                .build()
        return estimateAndSendTx(transaction)
    }

    TransactionResult callContract(Address contract, String method, Map<String, RpcItem> params) {
        var txHash = sendCall(contract, method, params)
        return waitResult(txHash)
    }

    /**
     * Query contract
     * @param contract Address of the contract
     * @param method Method to call for query
     * @param params Parameters to use for query
     * @return return value of the method.
     */
    RpcItem queryContract(Address contract, String method, Map<String, RpcItem> params) {
        var queryCall = new Call.Builder()
                .to(contract)
                .method(method)
                .params(asRpcItem(params))
                .build()
        return iconService.call(queryCall).execute()
    }

    /**
     * handleEvents starts the monitor, then it will dispatch events
     * to the handler.
     * It would stop without exception if the connection is closed by the server.
     * @param monitor Monitor instance to start
     * @param handler Event handler which returns true if it has finished his work.
     */
    static <T> void handleEvents(Monitor<T> monitor, Function<T, Boolean> handler) {
        var done = false
        long errorCode = 0
        monitor.start(new Monitor.Listener<T>() {
            @Override
            void onStart() {
                println("MONITOR started")
            }

            @Override
            void onEvent(T ev) {
                if (handler(ev)) {
                    monitor.stop()
                    done = true
                }
            }

            @Override
            void onError(long code) {
                println("MONITOR error=${code}")
                if (!done) {
                    errorCode = code
                    done = true
                }
            }

            @Override
            void onClose() {
                println("MONITOR closed")
                done = true
            }

            @Override
            void onProgress(BigInteger h) {
            }
        })

        while (!done) {
            sleep(1000)
        }
        if (errorCode != 0) {
            throw new Exception("Monitoring fails with code=${errorCode}")
        }
    }

    @Input
    @Optional
    abstract Property<Closure> getWork()

    def doWork(Closure w) {
        work.set(w)
    }

    @TaskAction
    void action() {
        if (work.isPresent()) {
            work.get().call()
        }
    }
}
