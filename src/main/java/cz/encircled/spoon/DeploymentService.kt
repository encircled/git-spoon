package cz.encircled.spoon

import com.fasterxml.jackson.databind.ObjectMapper
import cz.encircled.spoon.FileUtils.nestedPath
import cz.encircled.spoon.FileUtils.prepareCleanDirectory
import cz.encircled.spoon.Log.fatal
import cz.encircled.spoon.Log.info
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.set
import kotlin.concurrent.fixedRateTimer

class DeploymentService(private val configRepo: String, private val workingDir: String) {

    private val deployments: MutableMap<String, Deployment> = HashMap()

    init {
        prepareCleanDirectory(workingDir)
                .onError { error(it) }
                .onSuccess { info("Config repo: $configRepo, Working directory: $workingDir") }
                .onSuccess {
                    fixedRateTimer(name = "loop-conf", period = 2 * 10 * 1000) {
                        prepareCleanDirectory(nestedPath(workingDir, "config"))
                                .onError { e -> error(e) }
                                .thenExec("git clone --depth=1 $configRepo " + nestedPath(workingDir, "config"))
                                .onError { e -> Log.error("Failed to clone configuration git repository", e) }
                                .then { applyDeployments(loadConfiguration()!!) }
                    }
                }
    }

    private fun stringifyConfig() = deployments.map { it.value.params }.toString()

    private fun loadConfiguration(): DeploymentConfiguration? {
        val confFile = File(nestedPath(workingDir, "config", "configuration.json"))
        if (!confFile.exists()) fatal("Configuration repo must contain file configuration.json")

        return try {
            val parsed = ObjectMapper().readValue(confFile, DeploymentConfiguration::class.java)
            parsed.deployments.entries.forEach {
                it.value.name = it.key
                it.value.workingDir = workingDir
            }
            parsed
        } catch (e: Exception) {
            fatal("Error during parsing configuration.json", e.message ?: "")
            null
        }
    }

    private fun applyDeployments(conf: DeploymentConfiguration): ExecResult {
        var hasChange = false

        // Terminate and remove deleted and changed deployments
        deployments
                .filter {
                    conf.deployments[it.key] == null || conf.deployments[it.key] != it.value.params
                }
                .map { it.key }
                .forEach {
                    hasChange = true
                    deployments.remove(it)!!.terminate()
                }

        conf.deployments
                .filter { !deployments.containsKey(it.key) }
                .forEach {
                    hasChange = true
                    deployments[it.key] = Deployment(it.value)
                }

        if (hasChange) info("Configuration successfully updated", stringifyConfig())

        return ExecResult()
    }

}

object FileUtils {

    fun nestedPath(vararg tokens: String): String = tokens.joinToString(File.separator)

    fun prepareCleanDirectory(vararg paths: String): ExecResult {
        paths.forEach { path ->
            val dir = File(path)

            val error = if (dir.exists() && !dir.canWrite()) {
                "Write access right are required to the working directory $path"
            } else if (dir.exists() && !dir.deleteRecursively()) {
                "Failed to delete the working directory $path, this may cause failing of cloning git repo"
            } else if (!dir.mkdir()) {
                "Failed to create the working directory $path"
            } else ""

            if (error.isNotEmpty()) return ExecResult("", error)
        }

        return ExecResult()
    }

}

object Log {

    fun info(vararg msg: String): ExecResult =
            log("INFO", msg.toList())

    fun warn(vararg msg: String): ExecResult =
            log("WARN", msg.toList())

    fun error(vararg msg: String): ExecResult =
            log("ERROR", msg.toList())

    fun fatal(vararg msg: String) {
        log("ERROR", msg.toList())
        System.exit(-1)
    }

    private fun log(severity: String, msg: List<String>): ExecResult {
        val result = msg.joinToString("\n")
        val timestamp = SimpleDateFormat("HH:mm:ss dd.MM.yyyy").format(Date())

        println("$timestamp $severity msg: $result")

        return ExecResult()
    }

}

fun main(args: Array<String>) {
    val mappedArgs = args
            .filter { it.contains("=") }
            .associateBy(
                    { it.split("=")[0].replace("-", "") },
                    { it.split("=")[1] }
            )
    val configRepo = System.getenv("configRepo") ?: mappedArgs["configRepo"]
    val workingDir = System.getenv("workingDir") ?: mappedArgs["workingDir"] ?: "/tmp-dir"

    if (configRepo.isNullOrEmpty()) fatal("Configuration repository must be set either via args [--configRepo] or environment variables [configRepo]")

    DeploymentService(configRepo!!, workingDir)

    val server = embeddedServer(Netty) {
        routing {
            get("/") {
                call.respondText("{\"status\":1}", ContentType.Application.Json)
            }
        }
    }

    server.start(true)
}
