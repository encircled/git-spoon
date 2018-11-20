package cz.encircled.spoon

import kotlinx.coroutines.*

import java.io.File
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.concurrent.fixedRateTimer

/**
 * Represents app global parameters
 */
data class Params(var repository: String = "",
                  var namespace: String = "",
                  var workingDir: String = "",
                  var interval: Long = -1)

class Application(private val params: Params) {

    fun run() {
        info("", "Let the deployment begin",
                "source repo with manifests: ${params.repository}",
                "re-scan interval: ${params.interval / 1000} seconds", "")

        fixedRateTimer(name = "loop", period = params.interval) {
            GlobalScope.async {
                try {
                    perform()
                } catch (e: Exception) {
                    info("Whoops, something went wrong: ${e.message}")
                }
            }
        }

    }

    private fun perform() {
        prepareDirectory()
                .onError { error(it) }

                .then { info("Performing git clone") }
                .thenExec("git clone --depth=1 ${params.repository} ${params.workingDir}")
                .onError { error("Failed to clone git repository", it) }
                .onSuccess { info("Git repository successfully cloned") }

                .then { info("Applying changes to the cluster...") }
                .thenExec("kubectl apply --namespace=${params.namespace} -R --filename=${params.workingDir}")
                .onError { warn("Errors during applying changes", it) }
                .onSuccess { info("Done:", it) }

                .then { info("Zzzz...", "") }
    }

    private fun prepareDirectory(): ExecResult {
        val dir = File(params.workingDir)

        val error = if (!dir.canWrite()) {
            "Write access right are required to the working directory ${params.workingDir}"
        } else if (dir.exists() && !dir.deleteRecursively()) {
            "Failed to delete the working directory ${params.workingDir}, this may cause failing of cloning git repo"
        } else if (!dir.mkdir()) {
            "Failed to create the working directory ${params.workingDir}"
        } else ""

        return ExecResult("", error)
    }

}

private fun info(vararg msg: String): ExecResult =
        log("INFO", msg.toList())

private fun warn(vararg msg: String): ExecResult =
        log("WARN", msg.toList())

private fun error(vararg msg: String): ExecResult =
        log("ERROR", msg.toList())

private fun fatal(vararg msg: String) {
    log("ERROR", msg.toList())
    System.exit(-1)
}

private fun log(severity: String, msg: List<String>): ExecResult {
    val result = msg.joinToString("\n")
    val timestamp = SimpleDateFormat("HH:mm:ss dd.MM.yyyy").format(Date())

    println("$timestamp $severity msg: $result")

    return ExecResult()
}

fun main(args: Array<String>) {
    val params = Params()
    readParamsFromEnv(params)
    readParamsFromArgs(args, params)
    validateParams(params)

    Application(params).run()
}

private fun validateParams(params: Params) {
    if (params.repository.isEmpty()) fatal("Source repository must be set using --repo")
    if (params.namespace.isEmpty()) fatal("Kubernetes namespace must be set using --ns")
    if (params.interval < 0) fatal("Re-scan interval must be set using --ns")
    if (params.workingDir.isEmpty()) fatal("Working dir must be set using --ns")
}

private fun readParamsFromEnv(params: Params) {
    params.repository = System.getenv("repo") ?: ""
    params.namespace = System.getenv("ns") ?: ""
    params.interval = parseDuration(System.getenv("interval"))
    params.workingDir = System.getenv("dir") ?: ""
}

private fun readParamsFromArgs(args: Array<String>, params: Params) {
    args.forEach {
        when {
            it.startsWith("--repo=") -> params.repository = fromArg(it)
            it.startsWith("--ns=") -> params.namespace = fromArg(it)
            it.startsWith("--interval=") -> params.interval = parseDuration(fromArg(it))
            it.startsWith("--dir=") -> params.workingDir = fromArg(it)
            else -> throw RuntimeException("Unknown token [$it], supported args:\n--repo, --ns, --interval, --dir")
        }
    }
}

private fun parseDuration(source: String?): Long =
        if (source.isNullOrEmpty()) -1
        else Duration.parse("PT$source".toUpperCase()).toMillis()

private fun fromArg(it: String) = it.split("=")[1]