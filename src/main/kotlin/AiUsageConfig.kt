import generation.*
import io.vertx.core.Vertx
import io.vertx.ext.web.client.WebClient
import kotlinx.coroutines.sync.Mutex
import java.io.File

object AiUsageConfig {
    private val configFile = File("config/ai_usage.properties")
    var storageFolder = File("storage")

    var configData = HashMap<String,String> ()
    init {
        try {
            configFile.parentFile.mkdirs()
            configFile.readLines().forEach {
                val strings = it.split("=")
                configData[strings[0]] = strings[1]
            }
        }catch (e:Exception){
            println("No configFile. Use defaults")
        }
    }
    val videoExtension = configData["videoExtension"]?:"mkv"
    val audioExtension = configData["audioExtension"]?:"mp3"
    val imageExtension = configData["imageExtension"]?:"png"
    val maxTimeMs = 600_000L
    val fileAccessMutex = Mutex()
    val vertx = Vertx.vertx()
    val webClient = WebClient.create(vertx)
    var kieToken:String? = configData["KieToken"]
        set(value) {
            field = value
            configData["KieToken"]=value!!
            availableProviders = allProviders.filter { it.token.isNotEmpty() }
            saveConfig()
        }
    var openRouterToken:String? = configData["OpenRouterToken"]
        set(value) {
            field = value
            configData["OpenRouterToken"]=value!!
            availableProviders = allProviders.filter { it.token.isNotEmpty() }
            saveConfig()
        }
    var nanoGptToken:String? = configData["NanoGptToken"]
        set(value) {
            field = value
            configData["NanoGptToken"]=value!!
            availableProviders = allProviders.filter { it.token.isNotEmpty() }
            saveConfig()
        }
    var aigateToken:String? = configData["AiGateToken"]
        set(value) {
            field = value
            configData["AiGateToken"]=value!!
            availableProviders = allProviders.filter { it.token.isNotEmpty() }
            saveConfig()
        }
    var aiMlToken:String? = configData["aiMlToken"]
        set(value) {
            field = value
            configData["aiMlToken"]=value!!
            availableProviders = allProviders.filter { it.token.isNotEmpty() }
            saveConfig()
        }
    val allProviders = listOf(
        AiGate,
        Openrouter,
        Kie,
        AI_ML,
        NanoGpt,
    )
    var availableProviders = allProviders.filter { it.token.isNotEmpty() }


    init {
        println("availableProviders")
        availableProviders.forEach { provider -> println("Available provider: $provider") }
        availableProviders.forEach {
            it.updatePrices()
        }
    }
    fun saveConfig(){
        configFile.writeText(configData.map { "${it.key}=${it.value}" }.joinToString("\n"))
    }
}
