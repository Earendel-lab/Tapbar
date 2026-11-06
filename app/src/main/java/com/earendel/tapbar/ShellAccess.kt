package com.earendel.tapbar

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

object ShellAccess {

    const val METHOD_AUTO = 0
    const val METHOD_SHIZUKU = 1
    const val METHOD_ROOT = 2

    const val SHIZUKU_UNAVAILABLE = 0
    const val SHIZUKU_NEEDS_PERMISSION = 1
    const val SHIZUKU_READY = 2

    private const val PERMISSION_REQUEST_CODE = 4107
    private const val BIND_TIMEOUT_MS = 6000L
    private const val ROOT_TIMEOUT_SECONDS = 10L
    private const val MAX_COMMAND_LENGTH = 4096

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newCachedThreadPool()
    private val lock = Any()
    private var service: IShellService? = null
    private var binding = false
    private val waiting = ArrayList<(IShellService?) -> Unit>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val connected = if (binder != null && binder.pingBinder()) IShellService.Stub.asInterface(binder) else null
            val callbacks = synchronized(lock) {
                service = connected
                binding = false
                val copy = ArrayList(waiting)
                waiting.clear()
                copy
            }
            callbacks.forEach { it(connected) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) {
                service = null
                binding = false
            }
        }
    }

    fun shizukuState(): Int = try {
        when {
            !Shizuku.pingBinder() -> SHIZUKU_UNAVAILABLE
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> SHIZUKU_READY
            else -> SHIZUKU_NEEDS_PERMISSION
        }
    } catch (t: Throwable) {
        SHIZUKU_UNAVAILABLE
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (t: Throwable) {
        }
    }

    fun observeShizuku(onChange: () -> Unit): () -> Unit {
        val received = Shizuku.OnBinderReceivedListener { main.post(onChange) }
        val dead = Shizuku.OnBinderDeadListener { main.post(onChange) }
        val result = Shizuku.OnRequestPermissionResultListener { _, _ -> main.post(onChange) }
        Shizuku.addBinderReceivedListener(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(result)
        return {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(result)
        }
    }

    fun run(context: Context, command: String, method: Int, onDone: (Boolean) -> Unit) {
        val app = context.applicationContext
        val trimmed = command.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_COMMAND_LENGTH) {
            main.post { onDone(false) }
            return
        }
        worker.execute {
            val ok = execute(app, trimmed, method)
            main.post { onDone(ok) }
        }
    }

    fun runCustom(context: Context, command: String, method: Int) {
        val app = context.applicationContext
        if (command.isBlank()) {
            toast(app, R.string.shell_no_command)
            return
        }
        run(app, command, method) { ok ->
            if (!ok) toast(app, R.string.shell_failed)
        }
    }

    private fun toast(context: Context, resId: Int) {
        Toast.makeText(context, LocaleHelper.wrap(context).getString(resId), Toast.LENGTH_SHORT).show()
    }

    private fun execute(app: Context, command: String, method: Int): Boolean {
        if (method != METHOD_ROOT && shizukuState() == SHIZUKU_READY) {
            val code = viaShizuku(app, command)
            if (code != null) return code == 0
        }
        if (method == METHOD_SHIZUKU) return false
        return viaRoot(command) == 0
    }

    private fun viaShizuku(app: Context, command: String): Int? {
        val latch = CountDownLatch(1)
        var remote: IShellService? = null
        withService(app) {
            remote = it
            latch.countDown()
        }
        if (!latch.await(BIND_TIMEOUT_MS + 1000L, TimeUnit.MILLISECONDS)) return null
        val shell = remote ?: return null
        return try {
            shell.exec(command)
        } catch (t: Throwable) {
            null
        }
    }

    private fun viaRoot(command: String): Int? = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        process.outputStream.close()
        drainOutput(process)
        if (process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.exitValue()
        } else {
            process.destroyForcibly()
            null
        }
    } catch (t: Throwable) {
        null
    }

    private fun withService(app: Context, callback: (IShellService?) -> Unit) {
        val existing = synchronized(lock) {
            service?.takeIf { it.asBinder().pingBinder() }
        }
        if (existing != null) {
            callback(existing)
            return
        }
        val shouldBind = synchronized(lock) {
            waiting.add(callback)
            val start = !binding
            binding = true
            start
        }
        main.postDelayed({
            val timedOut = synchronized(lock) {
                val removed = waiting.remove(callback)
                if (removed && waiting.isEmpty()) binding = false
                removed
            }
            if (timedOut) callback(null)
        }, BIND_TIMEOUT_MS)
        if (shouldBind) {
            main.post {
                try {
                    Shizuku.bindUserService(serviceArgs(app), connection)
                } catch (t: Throwable) {
                    val callbacks = synchronized(lock) {
                        binding = false
                        val copy = ArrayList(waiting)
                        waiting.clear()
                        copy
                    }
                    callbacks.forEach { it(null) }
                }
            }
        }
    }

    private fun serviceArgs(app: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(app.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .version(1)
}

internal fun drainOutput(process: Process) {
    Thread {
        try {
            val buffer = ByteArray(1024)
            val stream = process.inputStream
            while (stream.read(buffer) >= 0) {
            }
        } catch (t: Throwable) {
        }
    }.apply { isDaemon = true }.start()
}
