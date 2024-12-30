package com.parameta.gradle

import foundation.icon.icx.data.Bytes
import foundation.icon.icx.data.TransactionResult
import foundation.icon.icx.transport.jsonrpc.RpcError
import foundation.icon.icx.transport.jsonrpc.RpcItem
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class ContractCallTask extends ContractTask {
    @Input
    abstract Property<String> getMethod()

    @Input
    @Optional
    abstract MapProperty<String, RpcItem> getParams()

    @Internal
    abstract Property<RpcItem> getQueryResult()

    @Internal
    abstract Property<Bytes> getTxHash()

    @Internal
    abstract Property<TransactionResult> getCallResult()

    @Input
    @Optional
    abstract Property<Closure> getOnQueryResult()
    def doOnQuery(Closure c) {
        onQueryResult.set(c)
    }

    @Input
    @Optional
    abstract Property<Closure> getOnCallResult()
    def doOnCall(Closure c) {
        onCallResult.set(c)
    }

    private static printParameters(String prefix, Map<String, RpcItem> params) {
        params.forEach { name, value -> {
            println("${prefix}${name} : ${value}")
        }}
    }

    static Object asTyped(String tname, RpcItem value) {
        if (value == null) {
            return null
        }
        if (tname=='str') {
            return value.asString()
        } else if (tname=='int') {
            return value.asInteger()
        } else if (tname=='Address') {
            return value.asAddress()
        } else if (tname=='bool') {
            return value.asBoolean()
        } else if (tname=='bytes') {
            return value.asBytes()
        } else if (tname=='dict') {
            return value.asObject()
        } else if (tname=='list') {
            return value.asArray()
        } else {
            throw new IllegalArgumentException("UnknownTypeName(name=${tname})")
        }
    }

    @TaskAction
    @Override
    void action() {
        var apis = iconService.getScoreApi(contract.get()).execute()
        var method = method.get()
        var api = apis.stream().filter(
                api -> api.getName() == method
        ).findAny()

        if (api.isEmpty()) {
            throw new Exception("MethodNotFound(name=${method})")
        }
        var apiInfo = api.get()

        if (apiInfo.getType() != "function") {
            throw new Exception("NotCallableType(name=${method})")
        }

        if (apiInfo.readonly == "0x1") {
            println("CALLING ${contract.get()}.${method}")
            printParameters("PARAM ", params.get())
            var value = queryContract(contract.get(), method, params.get())
            queryResult.set(value)
            if (onQueryResult.isPresent()) {
                onQueryResult.get().call(value)
            } else {
                var typedValue = asTyped(apiInfo.outputs[0].type, value)
                println("RESULT => ${typedValue}")
            }
        } else {
            Bytes hash
            println("CALLING ${contract.get()}.${method}")
            printParameters("PARAM ", params.get())
            try {
                hash = sendCall(contract.get(), method, params.get())
            } catch (RpcError err) {
                println("FAIL failure=${err.getMessage()}")
                return
            }

            txHash.set(hash)
            println("TRANSACTION id=${hash}")

            var txr = waitResult(hash)
            callResult.set(txr)
            if (onCallResult.isPresent()) {
                onCallResult.get().call(txr)
            } else {
                if (txr.getStatus() == BigInteger.ZERO) {
                    println("FAIL failure=${txr.getFailure()} ")
                } else {
                    println("SUCCESS")
                }
            }
        }
    }
}
