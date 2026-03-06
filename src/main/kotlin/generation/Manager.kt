// Manager.kt
package generation

import AiUsageConfig.maxTimeMs
import AiUsageScope
import cutJsonObject
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.launch
import suspendCoroutineWithTimeout
import java.io.File
import kotlin.coroutines.resume



suspend fun String.makeTranslation(lang:String="En"):String{
    val response = suspendCoroutineWithTimeout<String?>(maxTimeMs){cont->
        createText("""
            Translate the text to $lang
            Fill out this json:
            {"original":"$this","translation":""}
            In response, I only expect json without accompanying text.
        """.trimIndent(),{
            it?.printStackTrace()
            cont.resume(null)
        },{
            cont.resume(it)
        })
    }?:throw Exception("Cant translate the promt to $lang")
    val jsonString = response.cutJsonObject()
    val json = JsonObject(jsonString)
    return json.getString("translation")!!
}
class NoAPIProvidersException:Exception("no API providers")
fun createImageWithImageFiles(text: String, images:List<File>?, format:String, fileBase:String, onError:((Throwable?) -> Unit), handler:((File)->Unit)){

   val provider = AiUsageConfig.availableProviders.firstOrNull { it.getImageGenerationModels().isNotEmpty() }
   if(provider==null){
       onError(NoAPIProvidersException())
       return
   }

    provider.createImageWithImageFiles(text, images, format, fileBase,{}, onError) {
        handler(it)
        AiUsageConfig.availableProviders.forEach {
            it.updateBalance()
        }
    }
}

fun createText(text: String, onError:((Throwable?) -> Unit), onSuccess: (String) -> Unit){
    val providers = listOf(
        ProviderConfig(AiGate, ::tryAiGate, 3),    // 1 попытка DeepSeek
        ProviderConfig(NanoGpt, ::tryNanoGpt, 3),     // 3 попытки NanoGPT
        ProviderConfig(Openrouter, ::tryOpenRouter, 3)   // 3 попытки OpenRouter
    ).filter { AiUsageConfig.availableProviders.contains(it.apiProvider) }
    if(providers.isEmpty()){
        AiUsageScope.scope.launch {
            val result = suspendCoroutineWithTimeout<String?>(500_000){cont->
                AiGate.createText(text,{},{cont.resume(null)},{cont.resume(it)})
            }
            if(result!=null){
                onSuccess(result)
            }else{
                onError(Throwable("cant create text"))
            }
        }
        return

    }
    tryProvidersRecursively(providers, text, onError, {
        onSuccess(it)
        AiUsageConfig.availableProviders.forEach {
            it.updateBalance()
        }
    })
}

private suspend fun tryAiGate(prompt: String): String {
    return suspendCoroutineWithTimeout<String?>(500_000){cont->
        AiGate.createText(prompt,{},{cont.resume(null)},{cont.resume(it)})
    } ?:throw Exception("cant create text by AiGate")
}

private suspend fun tryNanoGpt(prompt: String): String {
    return suspendCoroutineWithTimeout<String?>(100_000){cont->
        NanoGpt.createText(prompt,{},{cont.resume(null)},{cont.resume(it)})
    } ?:throw Exception("cant create text by NanoGpt")
}

private suspend fun tryOpenRouter(prompt: String): String {
    return suspendCoroutineWithTimeout<String?>(100_000){cont->
        Openrouter.createText(prompt,{},{cont.resume(null)},{cont.resume(it)})
    } ?:throw Exception("cant create text by OpenRouter")
}

private data class ProviderConfig(
    val apiProvider: ApiProvider,
    val provider: suspend (String) -> String,
    val maxAttempts: Int
)

private fun tryProvidersRecursively(
    providers: List<ProviderConfig>,
    text: String,
    onError: (Throwable?) -> Unit,
    handler: (String) -> Unit,
    currentProviderIndex: Int = 0,
    currentAttempt: Int = 1
) {
    if (currentProviderIndex >= providers.size) {
        onError(RuntimeException("All providers exhausted"))
        return
    }

    val config = providers[currentProviderIndex]

    AiUsageScope.scope.launch {
        try {
            println("tryProvidersRecursively currentProviderIndex=$currentProviderIndex currentAttempt=$currentAttempt")
            val result = config.provider(text)
           // println("have result: $result")
            handler(result)
        } catch (e: Throwable) {
            println("❌ Attempt $currentAttempt/${config.maxAttempts} failed for provider ${currentProviderIndex}: ${e.message}")

            if (currentAttempt < config.maxAttempts) {
                // Повторная попытка с тем же провайдером
                tryProvidersRecursively(providers, text, onError, handler, currentProviderIndex, currentAttempt + 1)
            } else {
                // Переход к следующему провайдеру
                tryProvidersRecursively(providers, text, onError, handler, currentProviderIndex + 1)
            }
        }
    }
}
