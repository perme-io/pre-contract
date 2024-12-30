package com.parameta.gradle

import foundation.icon.icx.data.Address
import foundation.icon.icx.transport.monitor.Monitor
import org.gradle.api.tasks.Input
import org.gradle.api.provider.Property
import org.gradle.api.tasks.options.Option

import java.util.function.Function

abstract class ContractTask extends ICONTask {
    @Input
    abstract Property<Address> getContract()

    @Option(option="contract", description = "Contract address to interact")
    void setContractOption(String v) {
        contract.set(asAddress(v))
    }
}
