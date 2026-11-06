package com.earendel.tapbar

import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class ShellUserService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): Int {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.outputStream.close()
            drainOutput(process)
            if (process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.exitValue()
            } else {
                process.destroyForcibly()
                -1
            }
        } catch (t: Throwable) {
            -1
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
