package generation


import AiUsageConfig
import AiUsageConfig.audioExtension
import AiUsageConfig.fileAccessMutex
import AiUsageConfig.imageExtension
import AiUsageConfig.maxTimeMs
import AiUsageConfig.vertx
import AiUsageConfig.videoExtension
import AiUsageConfig.webClient
import AiUsageLoggers
import AiUsageScope
import com.google.gson.JsonParser
import extractKieAIUrlFromResponse
import generation.voice.ElevenLabsVoices
import getMimeType
import getNextNoExistFile
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.RequestOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import suspendCoroutineWithTimeout
import toTranslit
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlin.random.nextUInt


data class ChatMessage(val text:String, val isUser:Boolean, val isCompleted:Boolean, val id:Int)
data class Chat(val messages: List<ChatMessage>)
sealed class ApiProvider() {
    var balanceUpdateListener:((Float)->Unit)?=null
    abstract val accountUrl:String
    var prices = HashMap<String,Double>()
    var balance:Float? = null
        set(value) {
            field = value
            if (value != null) {
                balanceUpdateListener?.invoke(value)
            }
        }
    open fun updatePrices(){

    }

    abstract val token :String

    internal open fun startUpdateBalance(){

    }
    internal var updateJob: Job? = null

    open val uploadFun: (suspend (File)->String?)? = null

    fun updateBalance():Job{
        if(updateJob?.isActive == true )return updateJob!!
        startUpdateBalance()
        return updateJob!!
    }
    fun getVideoModels():Set<VideoGenerationModel>{
        return VideoGenerationModel.entries.filter { it.modelNames[this] != null }.toSet()
    }
    fun getTextModels():Set<TextGenerationModel>{
        return TextGenerationModel.entries.filter { it.modelNames[this] != null }.toSet()
    }
    fun getSoundEffectGenerationModels():Set<SoundEffectGenerationModel>{
        return SoundEffectGenerationModel.entries.filter { it.modelNames[this] != null }.toSet()
    }
    fun getVoiceIsolationModels():Set<SoundVoiceIsolationModel>{
        return SoundVoiceIsolationModel.entries.filter { it.modelNames[this] != null }.toSet()
    }
    fun getVoiceGenerationModels():Set<VoiceGenerationModel>{
        return VoiceGenerationModel.entries.filter { it.modelNames[this] != null }.toSet()
    }
    fun getImageGenerationModels():Set<ImageGenerationModel>{
        return ImageGenerationModel.entries.filter { it.modelNames[this] != null }.toSet()
    }
    open fun createVideoWithImageFiles(videoGenerationModel: VideoGenerationModel,
                                       prompt: String,
                                       images: List<File>?,
                                       duration: String,
                                       aspectRatio: String,
                                       resolution: String,
                                       fileBase:String,
                                       haveTaskId:((String)->Unit),
                                       onError: (Throwable) -> Unit,
                                       resultHandler: (File) -> Unit){
        AiUsageScope.scope.launch {
            val uploadedImages = images?.map{image->
                AiUsageScope.scope.async {
                return@async if(uploadFun!=null && image!=null){
                    uploadFun!!(image)!!
                }else{
                    null
                }
            }}?.awaitAll()?.filterNotNull()

            createVideoWithImageUrls(videoGenerationModel, prompt, uploadedImages, duration, aspectRatio, resolution, fileBase, haveTaskId, onError, resultHandler)
        }
    }
    open fun createVideoWithImageUrls(videoGenerationModel: VideoGenerationModel,
                                     prompt: String,
                                     imageUrls: List<String>?,
                                     duration: String,
                                     aspectRatio: String,
                                     resolution: String,
                                     fileBase:String,
                                     haveTaskId:((String)->Unit),
                                     onError: (Throwable) -> Unit,
                                     resultHandler: (File) -> Unit){
        throw Exception("video generation is not supported for ${this.javaClass.simpleName}")
    }
    open fun createText(prompt:String, haveTaskId:((String)->Unit), onError:(Throwable?)->Unit, handler:(String) -> Unit){
        throw Exception("text generation is not supported for ${this.javaClass.simpleName}")
    }
    open fun chat(
        chat: Chat,
        onError: (Throwable?) -> Unit,
        handler: (ChatMessage) -> Unit){
        throw Exception("text generation is not supported for ${this.javaClass.simpleName}")
    }
    open fun createImageWithImageFiles(
        prompt:String,
        images:List<File>?,
        format:String? = "auto",
        fileBase:String,
        haveTaskId:((String)->Unit),
        onError: ((Throwable)->Unit),
        handler:((File) -> Unit)){
        AiUsageScope.scope.launch {
            val uploadedImages = images?.map {image->
                AiUsageScope.scope.async {
                    uploadFun?.let { it(image) }!!
                }
            }?.awaitAll()
            createImageWithImageUrls(prompt, uploadedImages, format, fileBase, haveTaskId, onError, handler)
        }

    }
    open fun createImageWithImageUrls(
        prompt:String,
        imagesUrls:List<String>?,
        format:String? = "auto",
        fileBase:String,
        haveTaskId:((String)->Unit),
        onError: ((Throwable)->Unit),
        handler:((File) -> Unit)){
        throw Exception("image generation is not supported for ${this.javaClass.simpleName}")
    }

    open fun createSoundEffect(
        soundEffectGenerationModel: SoundEffectGenerationModel,
        prompt:String,
        durationMs:Int,
        isLoop:Boolean,
        haveTaskId:((String)->Unit),
        onError:(Throwable?)->Unit,
        handler:(File) -> Unit
    ){
        throw Exception("create Sound Effect is not supported for ${this.javaClass.simpleName}")
    }

    open fun voiceGeneration(
        voiceGenerationModel: VoiceGenerationModel,
        voice: ElevenLabsVoices,
        text:String,
        stability:Float = 0.5f,
        similarity_boost:Float = 0.75f,
        style:Float = 0f,
        speed:Float = 1f,
        previousText:String? = null,
        nextText:String? = null,
        fileBase:String,
        haveTaskId:((String)->Unit),
        onError:(Throwable?)->Unit = {},
        handler:(File) -> Unit = {}
    ){
        throw Exception("Voice generation is not supported for ${this.javaClass.simpleName}")
    }

}

data object Kie : ApiProvider() {
    override val accountUrl: String = "https://kie.ai/api-key"
    override val token :String
        get() {return AiUsageConfig.kieToken?:""}
    val generateVeoUrl = "https://api.kie.ai/api/v1/veo/generate"
    val generateUrl = "https://api.kie.ai/api/v1/jobs/createTask"

    val checkUrlVeo = "https://api.kie.ai/api/v1/veo/record-info"
    val checkUrl = "https://api.kie.ai/api/v1/jobs/recordInfo"

    val downloadVeoFullHdUrl = "https://api.kie.ai/api/v1/veo/get-1080p-video"
    val uploadUrl = "https://kieai.redpandaai.co/api/file-stream-upload"
    override val uploadFun: (suspend (File) -> String?) = {file->
        val buffer = Buffer.buffer(file.readBytes())
        val requestBody = io.vertx.ext.web.multipart.MultipartForm.create()
            .attribute("uploadPath", "temp") // Добавляем uploadPath, можно изменить на нужный путь
            .binaryFileUpload(
                "file",           // имя поля для файла
                file.name,        // имя файла
                buffer,
                file.getMimeType()
            )

        suspendCoroutineWithTimeout<String?>(30_000) { cont ->
            try {
                webClient.postAbs(uploadUrl)
                    .putHeader("Authorization", "Bearer $token")
                    .sendMultipartForm(requestBody)
                    .onSuccess { response ->
                        when (response.statusCode()) {
                            200 -> {
                                val responseBody = response.bodyAsString()
                                println("responseBody $responseBody")
                                val fileUrl = extractKieAIUrlFromResponse(responseBody)
                                cont.resume(fileUrl.also {
                                    println("File $this uploaded to KIE AI: $it")
                                })
                            }
                            401 -> {
                                println("KIE AI upload failed: Unauthorized - check API key")
                                cont.resume(null)
                            }
                            400 -> {
                                println("KIE AI upload failed: Bad Request - ${response.bodyAsString()}")
                                cont.resume(null)
                            }
                            else -> {
                                println("KIE AI upload failed: ${response.statusCode()} - ${response.bodyAsString()}")
                                cont.resume(null)
                            }
                        }
                    }.onFailure {
                        println("KIE AI upload error: ${it.message}")
                        cont.resume(null)
                    }
            } catch (e: Exception) {
                println("KIE AI upload exception: ${e.message}")
                cont.resume(null)
            }
        }
    }






    override fun startUpdateBalance(){
        updateJob=AiUsageScope.lowPriorityScope.launch {
            try {
                println("Start update balance for Kie")
                val response = webClient.getAbs("https://api.kie.ai/api/v1/chat/credit")
                    .putHeader("Authorization", "Bearer $token")
                    .send()
                    .await()

                println("✅ Received response, status: ${response.statusCode()}")

                if (response.statusCode() == 200) {
                    val responseBody = response.bodyAsJsonObject()
                    balance = responseBody.getString("data").toFloat()
                }
            } catch (e: Exception) {
                println("💥 Request failed: ${e.message}")
                e.printStackTrace()
            }finally {
                updateJob=null
            }
        }

    }
    override fun voiceGeneration(
        voiceGenerationModel: VoiceGenerationModel,
        voice: ElevenLabsVoices,
        text: String,
        stability: Float,
        similarity_boost: Float,
        style:Float,
        speed: Float,
        previousText:String?,
        nextText:String?,
        fileBase:String,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            val fileChoose:(()->File) = {
                getNextNoExistFile("sounds", fileBase, audioExtension)
            }
            try {
                val body = JsonObject().apply {
                    put("model", voiceGenerationModel.modelNames[this@Kie])
                    put("input", JsonObject().apply {
                        put("text", text)
                        put("voice", voice.name)
                        put("stability", stability)
                        put("similarity_boost", similarity_boost)
                        put("style", style)
                        put("speed", speed)
                        put("timestamps", false)
                        previousText?.let { put("previous_text", it) }
                        nextText?.let { put("next_text", it) }
                    })

                }
                webClient.postAbs(generateUrl)
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode() == 200) {
                                println("task created: ${response.bodyAsString()}")
                                val json = response.bodyAsJsonObject()

                                // Предполагаемая структура ответа
                                val taskId = json.getString("taskId") ?:
                                json.getJsonObject("data")?.getString("taskId")

                                if (taskId != null) {
                                    haveTaskId(taskId)
                                    println("Task ID: $taskId")
                                    // Запускаем процесс проверки статуса задачи
                                    awaitAndDownload(taskId,
                                        fileChoose,
                                        onError,
                                        { println("voice not ready") },
                                        handler)

                                } else {
                                    onError(Exception("No task ID in response: ${response.bodyAsString()}"))
                                }
                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage+response.statusCode()))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error during generation: ${error.message}"))
                    }
            }catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    override fun createSoundEffect(
        soundEffectGenerationModel: SoundEffectGenerationModel,
        prompt: String,
        durationMs: Int,
        isLoop: Boolean,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            try {
                val fileChoose:(()->File) = {
                    getNextNoExistFile("sounds", prompt.toTranslit().take(20), audioExtension)
                }
                val body = JsonObject().apply {
                    put("model", soundEffectGenerationModel.modelNames[this@Kie])
                    put("input", JsonObject().apply {

                        put("text", prompt)
                        put("loop", isLoop)
                        put("duration_seconds", durationMs/1000f)
                        put("prompt_influence", 0.3)
                        put("output_format", "mp3_44100_128")
                    })
                }
                webClient.postAbs(generateUrl)
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode() == 200) {
                                println("task created: ${response.bodyAsString()}")
                                val json = response.bodyAsJsonObject()

                                // Предполагаемая структура ответа
                                val taskId = json.getString("taskId") ?:
                                json.getJsonObject("data")?.getString("taskId")

                                if (taskId != null) {
                                    haveTaskId(taskId)
                                    println("Task ID: $taskId")
                                    // Запускаем процесс проверки статуса задачи
                                    awaitAndDownload(taskId,
                                        fileChoose,
                                        onError,
                                        { println("sound_effect not ready") },
                                        handler
                                    )

                                } else {
                                    onError(Exception("No task ID in response: ${response.bodyAsString()}"))
                                }
                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage+response.statusCode()))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error during generation: ${error.message}"))
                    }
            }catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    override fun createVideoWithImageUrls(
        videoGenerationModel: VideoGenerationModel,
        prompt: String,
        imageUrls: List<String>?,
        duration: String,
        aspectRatio: String,
        resolution: String,
        fileBase: String,
        haveTaskId: (String) -> Unit,
        onError: (Throwable) -> Unit,
        resultHandler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            try {
                val fileChoose:(()->File) = {
                    getNextNoExistFile("video", fileBase, videoExtension)
                }
                var createTaskUrl = when(videoGenerationModel) {
                    VideoGenerationModel.Veo3_1_Quality, VideoGenerationModel.Veo3_1Fast -> generateVeoUrl
                    else -> generateUrl
                }
                val body = JsonObject()

                when (videoGenerationModel) {
                    VideoGenerationModel.Veo3_1_Quality, VideoGenerationModel.Veo3_1Fast -> {
                        body.put("prompt", prompt)
                       /* imageUrls?.let{
                            body.put("imageUrls", JsonArray().also {
                                imageUrls.forEach { imageUrl ->it.add(imageUrl) }
                            })
                        }*/

                        body.put("model", videoGenerationModel.modelNames[this@Kie])
                        body.put("aspectRatio", aspectRatio)
                        body.put("seeds", 10001)
                        body.put("enableFallback", false)
                        body.put("enableTranslation", false)
                    }
                    else-> {
                        val input = JsonObject().apply {
                            put("prompt", prompt)
                            when(videoGenerationModel){
                                VideoGenerationModel.Sora2 ->  put("n_frames", duration)
                                else ->  put("duration", duration)
                            }
                            if(videoGenerationModel == VideoGenerationModel.Sora2){
                                val ratioValues = aspectRatio.split(":").map { it.toInt() }
                                if(ratioValues[0]>ratioValues[1])put("aspect_ratio", "landscape")
                                else put("aspect_ratio", "portrait")

                            }
                            put("duration", duration) // 5 секунд
                            if (videoGenerationModel == VideoGenerationModel.Kling_2_5_Pro){
                                put("cfg_scale", 0.5)
                                put("negative_prompt", "blur, distort, and low quality")
                            }
                            if(videoGenerationModel == VideoGenerationModel.Kling2_6){
                                put("sound", true)
                            }


                            /*imageUrls?.let { imgFiles ->
                                when(videoGenerationModel) {
                                    VideoGenerationModel.Kling2_6, VideoGenerationModel.Sora2->
                                    else-> put("image_url", imgFile)
                                }

                            }*/
                        }

                        body.put("model", videoGenerationModel.modelNames[this@Kie])
                        // put("callBackUrl", ProjectConfig.callbackUrl) // Нужно настроить в ProjectConfig
                        body.put("input", input)
                    }
                }
                val input = try{
                    body.getJsonObject("input")?: body
                }catch (e:Throwable){
                    body
                }
                //resolution
                when(videoGenerationModel){
                    VideoGenerationModel.Veo3_1_Quality, VideoGenerationModel.Veo3_1Fast -> {}
                    else->{
                        input.put("resolution", resolution)

                    }
                }

                //addimages
                if(imageUrls?.isNotEmpty()==true){

                    if(imageUrls.size==1 && videoGenerationModel in listOf(VideoGenerationModel.Hailuo2_3, VideoGenerationModel.Wan2_6) ){

                        input.put("image_url", imageUrls.first())
                    }else{
                        input.put("image_urls", JsonArray().also{
                            imageUrls.forEach { imageUrl -> it.add(imageUrl) }
                        })
                    }
                }

                AiUsageLoggers.tasks.log(body.toString())


                webClient.postAbs(createTaskUrl)
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode() == 200) {
                                println("task created: ${response.bodyAsString()}")
                                val json = response.bodyAsJsonObject()

                                // Предполагаемая структура ответа
                                val taskId = json.getString("taskId") ?:
                                json.getJsonObject("data")?.getString("taskId")

                                if (taskId != null) {
                                    haveTaskId(taskId)
                                    println("Task ID: $taskId")
                                    // Запускаем процесс проверки статуса задачи
                                    when(videoGenerationModel){
                                        VideoGenerationModel.Veo3_1_Quality, VideoGenerationModel.Veo3_1Fast -> {
                                            awaitAndDownloadVeo(
                                                taskId,
                                                fileChoose,
                                                onError,
                                                { println("video not ready") },
                                                resultHandler)
                                        }
                                        else->{
                                            awaitAndDownload(taskId,
                                                fileChoose,
                                                onError,
                                                { println("video not ready") },
                                                resultHandler)
                                        }
                                    }

                                } else {
                                    onError(Exception("No task ID in response: ${response.bodyAsString()}"))
                                }
                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage+response.statusCode()))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error during generation: ${error.message}"))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }


    override fun createImageWithImageUrls(
        prompt: String,
        imagesUrls: List<String>?,
        format: String?,
        fileBase: String,
        haveTaskId: (String) -> Unit,
        onError: (Throwable) -> Unit,
        handler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            try{
                val fileChoose:(()->File) = {
                    getNextNoExistFile("images", fileBase, imageExtension)
                }
                println("start nanoBanana generation")
                val body = JsonObject()
                val model =  if(imagesUrls?.isNotEmpty() == true){
                    ImageGenerationModel.NanoBananaEdit
                }else{

                    ImageGenerationModel.NanoBanana
                }
                body.put("model",model.modelNames[this@Kie])

                body.put("input",JsonObject().also { jsonObject ->
                    jsonObject.put("prompt",prompt)

                    imagesUrls?.let{
                        jsonObject.put("image_urls",JsonArray().also { array->
                            imagesUrls.forEach { array.add(it) }
                        })
                    }

                    jsonObject.put("output_format","png")
                    jsonObject.put("image_size",format)
                })
                webClient.postAbs(generateUrl)
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess {
                        if(it.statusCode() == 200){
                            println("success^${it.bodyAsString()}")
                            val json = it.bodyAsJsonObject()
                            val data = json.getJsonObject("data")
                            val taskId = data.getString("taskId")
                            haveTaskId(taskId)
                            val state = data.getString("state")
                            println("taskId: $taskId state: $state")
                            //processTaskId(taskId, onError,handler)
                            awaitAndDownload(taskId,
                                fileChoose,
                                onError,
                                { println("image tot ready") },
                                handler)

                        }else {
                            println("error ${it.statusCode()}:${it.bodyAsString()}")
                        }
                    }
                    .onFailure {
                        println("failed")
                        onError(it)
                    }
            }catch (e:Throwable){

                onError(e)
            }

        }
    }


    fun awaitAndDownload(
        taskId: String,
        fileChoose: () -> File,
        onError: (Throwable) -> Unit = {},
        notReady: () -> Unit,
        resultHandler: (File) -> Unit){
        AiUsageScope.lowPriorityScope.launch {
            var repeats = 60
            var resultUrl:String? = null
            var resultFile:File?=null
            delay(3_000)
            var errorMsg:String? = null
            while (resultFile == null && --repeats >= 0) {

                if(resultUrl==null){
                    try {
                        downloadVideoV2(taskId,
                            fileChoose,
                            {
                                resultUrl=it
                            },
                            { error ->
                                repeats=-1
                                errorMsg = error.message
                            },
                            notReady,) { downloadedFile ->
                            println("saveFile:$downloadedFile exist=${downloadedFile.exists()}")
                            resultFile = downloadedFile
                            resultHandler(downloadedFile)

                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                delay(10_000)
            }

            if (resultFile == null) {
                onError(Exception(errorMsg?:"Video generation fails."))
            }
        }
    }
    fun awaitAndDownloadVeo(
        taskId: String,
        fileChoose: () -> File,
        onError: (Throwable) -> Unit = {},
        notReady: () -> Unit,
        resultHandler: (File) -> Unit){
        AiUsageScope.lowPriorityScope.launch {
            var repeats = 60
            var resultUrl:String? = null
            var resultFile:File?=null
            delay(30_000)
            var errorText:String? = null
            while (resultFile == null && --repeats >= 0) {
                delay(10_000)
                if(resultUrl==null){
                    try {
                        downloadVideoVeo(
                            taskId,
                            fileChoose,
                            {
                                resultUrl=it
                            },
                            { error ->
                                errorText=error.message
                                repeats=-1
                            },
                            notReady) { downloadedFile ->
                            println("saveFile:$downloadedFile exist=${downloadedFile.exists()}")
                            resultFile=downloadedFile
                            resultHandler(downloadedFile)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            if(resultFile == null){
                onError(Exception(errorText?:"Video generation timeout after 600 seconds"))
            }
        }
    }
    fun downloadVideoV2(taskId: String,
                        fileChoose: () -> File,
                        haveUrl:(String) -> Unit,
                        onError: (Throwable) -> Unit = {},
                        notReady: () -> Unit,
                        resultHandler: (File) -> Unit) {
        webClient.getAbs("$checkUrl?taskId=$taskId")
            .putHeader("Authorization", "Bearer $token")
            .send()
            .onSuccess { response ->
                try {
                    if (response.statusCode() == 200) {
                        println("success:${response.bodyAsString()}")
                        val json = response.bodyAsJsonObject()



                        val data = json.getJsonObject("data")
                        val state = data.getString("state")
                        when(state){
                            "fail" -> onError(Exception(data.getString("failMsg")))
                            "success" -> {
                                val resultJson = JsonObject(data.getString("resultJson"))
                                val resultUrls = resultJson.getJsonArray("resultUrls")
                                val videoUrl = resultUrls.first().toString()
                                if(videoUrl.startsWith("http")) {
                                    haveUrl(videoUrl)
                                    downloadFromUrl(videoUrl, taskId , fileChoose, onError, resultHandler)
                                }else{
                                    notReady()
                                }
                            }
                            "generating","waiting"-> notReady()
                        }

                    } else {
                        val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                        println(errorMessage)
                        notReady()
                       // onError(Exception(errorMessage + response.statusCode()))
                    }
                } catch (e: Exception) {
                   e.printStackTrace()
                }
            }
            .onFailure { e ->
                e.printStackTrace()
            }
    }



    fun downloadVideoVeo(
        taskId: String,
        fileChoose:()->File,
        haveUrl:(String) -> Unit,
        onError: (Throwable) -> Unit = {},
        notReady: () -> Unit,
        handler: (File) -> Unit) {
        webClient.getAbs("$checkUrlVeo?taskId=$taskId")
            .putHeader("Authorization", "Bearer $token")
            .send()
            .onSuccess { response ->
                try {
                    if (response.statusCode() == 200) {
                        println("success:${response.bodyAsString()}")
                        val json = response.bodyAsJsonObject()

                        val code = json.getInteger("code")

                        when (code) {
                            200 -> {

                                val data = json.getJsonObject("data")

                                val errorMessage = data.getString("errorMessage")
                                if(errorMessage?.isNotEmpty()==true){
                                    onError(Exception(errorMessage))
                                    return@onSuccess
                                }
                                //val responseData = data.getJsonObject("response")
                                val resultUrl = data.getString("result_url")?:
                                data.getJsonObject("response")?.getJsonArray("resultUrls")?.first().toString()

                                if (!resultUrl.contains("http")) {
                                    notReady()
                                    return@onSuccess
                                }
                                haveUrl(resultUrl)
                                // Скачиваем первое доступное видео
                                downloadFromUrl(resultUrl, taskId, fileChoose, onError, handler)
                            }
                            422 -> onError(Exception("Unknown msg: ${json.getString("msg")}"))
                            else -> onError(Exception("Unknown successFlag: $code"))
                        }
                    } else {
                        val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                        onError(Exception(errorMessage + response.statusCode()))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError(e)
                }
            }
            .onFailure { error ->
                onError(Exception("Network error: ${error.message}"))
            }
    }
}

data object NanoGpt: ApiProvider(){
    override val accountUrl: String = "https://nano-gpt.com/api"
    override val token:String
        get() {return AiUsageConfig.nanoGptToken?:""}
    val generateUrl = "https://nano-gpt.com/api/generate-video"
    val generateTTSUrl = "https://nano-gpt.com/api/tts"
    val getResultUrl = "https://nano-gpt.com/api/generate-video/status"
    val completionsUrl = "https://nano-gpt.com/api/v1/chat/completions"
    val balanceUrl = "https://nano-gpt.com/api/check-balance"
    val pricesUrl = "https://nano-gpt.com/pricing"

    override fun startUpdateBalance(){
        updateJob=AiUsageScope.lowPriorityScope.launch {
            try{
                val responce = webClient.postAbs(balanceUrl)
                    .putHeader("x-api-key", token)
                    .send()
                    .await()
                val json = responce.bodyAsJsonObject()
                balance = json.getString("usd_balance").toFloat()
                //val nano = json.getString("nano_balance").toFloat()
                //var result = "$usd $"
                //if(nano>0) result += nano.toString() + " nano"
            }catch (e:Throwable){
                AiUsageLoggers.exceptions.log(e)
            }finally {
                updateJob = null
            }
        }


    }
    override fun createText(
        prompt: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (String) -> Unit) {
        AiUsageScope.scope.launch {
            try {
                val model=getTextModels().first().modelNames[this@NanoGpt]
                println("start text generation by NanoGpt $model")
                val body = JsonObject().apply {
                    put("model", model)
                    put("messages",
                        JsonArray()
                            .add(
                                JsonObject().put("role", "user")
                            )
                            .add(
                                JsonObject().put("content",prompt)
                            )
                    )
                    put("stream", false)

                }




                webClient.postAbs(completionsUrl)
                    .putHeader("x-api-key", token)
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode()/100 == 2) {
                                val string = response.bodyAsString()
                                val lines = string.split("\n").mapNotNull {
                                    try{
                                        val json = JsonParser.parseString(it.substringAfter("data: ")).asJsonObject
                                        val choices = json.get("choices").asJsonArray
                                        choices.joinToString("") { it.asJsonObject.get("delta").asJsonObject.get("content").asString }
                                    }catch (e:Throwable){null}
                                }
                                val result = lines.joinToString("")
                                handler(result)

                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage+response.statusCode()))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error during generation: ${error.message}"))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }


    override fun voiceGeneration(
        voiceGenerationModel: VoiceGenerationModel,
        voice: ElevenLabsVoices,
        text: String,
        stability: Float,
        similarity_boost: Float,
        style:Float,
        speed: Float,
        previousText: String?,
        nextText: String?,
        fileBase: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            val fileChoose:(()->File) = {
                getNextNoExistFile("sounds", fileBase, audioExtension)
            }
            try {
                println("start voice generation")
                val body = JsonObject().apply {
                    put("model", voiceGenerationModel.modelNames[this@NanoGpt])
                    put("text", text)
                    put("voice", voice.name)

                }




                webClient.postAbs(generateTTSUrl)
                    .putHeader("x-api-key", token)
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode()/100 == 2) {
                                println("task created: ${response.bodyAsString()}")
                                val json = response.bodyAsJsonObject()

                                // Предполагаемая структура ответа
                                val taskId = json.getString("runId") ?:
                                json.getJsonObject("data")?.getString("runId")

                                if (taskId != null) {
                                    println("Task ID: $taskId")
                                    // Запускаем процесс проверки статуса задачи
                                    awaitAndDownload(taskId,
                                        voiceGenerationModel.modelNames[this@NanoGpt]!!,
                                        fileChoose,
                                        onError,
                                        { println("video not ready") },
                                        handler)

                                } else {
                                    onError(Exception("No task ID in response: ${response.bodyAsString()}"))
                                }
                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage+response.statusCode()))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error during generation: ${error.message}"))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }
    override fun createVideoWithImageFiles(
        videoGenerationModel: VideoGenerationModel,
        prompt: String,
        images: List<File>?,
        duration: String,
        aspectRatio :String,
        resolution: String,
        fileBase:String,
        haveTaskId:((String)->Unit),
        onError: (Throwable) -> Unit,
        resultHandler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            try {
                val fileChoose:(()->File) = {
                    getNextNoExistFile("video",fileBase, videoExtension)
                }
                val body = JsonObject().apply {
                    put("model", videoGenerationModel.modelNames[this@NanoGpt])
                    put("prompt", prompt)
                    put("aspect_ratio", aspectRatio)
                    put("seconds", duration) // 5 секунд
                    if (videoGenerationModel == VideoGenerationModel.Kling_2_5_Pro){
                        put("cfg_scale", 0.5)
                        put("negative_prompt", "blur, distort, and low quality")
                    }
                    put("resolution", resolution)

                    if((images?.size?: 0) > 1){
                        images?.let { imgFiles ->
                            put("imageUrls", JsonArray().also {array->
                                imgFiles.forEach {imgFile->
                                    AiGate.uploadFun.let {
                                        array.add(it(imgFile))
                                    }
                                }
                            })
                        }
                    }else{
                        images?.let { imgFiles ->
                            put("imageUrl",
                                AiGate.uploadFun?.let { it(imgFiles.first()) })
                        }
                    }

                }




                webClient.postAbs(generateUrl)
                    .putHeader("x-api-key", token)
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode()/100 == 2) {
                                println("task created: ${response.bodyAsString()}")
                                val json = response.bodyAsJsonObject()

                                // Предполагаемая структура ответа
                                val taskId = json.getString("runId") ?:
                                json.getJsonObject("data")?.getString("runId")

                                if (taskId != null) {
                                    haveTaskId(taskId)
                                    println("Task ID: $taskId")
                                    // Запускаем процесс проверки статуса задачи
                                    awaitAndDownload(taskId,
                                        videoGenerationModel.modelNames[this@NanoGpt]!!,
                                        fileChoose,
                                        onError,
                                        { println("video not ready") },
                                        resultHandler)

                                } else {
                                    onError(Exception("No task ID in response: ${response.bodyAsString()}"))
                                }
                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage+response.statusCode()))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error during generation: ${error.message}"))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }
    fun awaitAndDownload(taskId: String, modelSlug:String, fileChoose:()->File, onError: (Throwable) -> Unit = {}, notReady:()->Unit, handler: (File) -> Unit){
        AiUsageScope.lowPriorityScope.launch {

            var repeats = 100
            var resultUrl:String? = null
            var resultFile:File?=null
            delay(3_000)
            while (resultFile == null && --repeats >= 0) {

                if(resultUrl == null){
                    try {
                        downloadVideoV2(taskId, fileChoose,
                            {
                            resultUrl=it
                        },modelSlug, { error ->
                            repeats=-1
                        }) { downloadedFile ->

                            println("saveFile:$downloadedFile exist=${downloadedFile.exists()}")
                            resultFile = downloadedFile
                            handler(downloadedFile)

                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                delay(10_000)
            }

            if (resultFile == null) {
                onError(Exception("Video generation fails"))
            }
        }
    }
    private fun downloadVideoV2(taskId: String, fileChoose: () -> File,  haveUrl:(String) -> Unit, modelSlug:String, onError: (Throwable) -> Unit = {}, handler: (File) -> Unit) {
        webClient.getAbs("$getResultUrl?runId=$taskId&modelSlug=$modelSlug")
            .putHeader("x-api-key", token)
            .send()
            .onSuccess { response ->
                try {
                    if (response.statusCode()/100 == 2) {
                        println("success:${response.bodyAsString()}")
                        val json = response.bodyAsJsonObject()



                        val data = json.getJsonObject("data")
                        val state = data.getString("status")
                        when(state){
                            "FAILED" -> onError(Exception(data.getString("userFriendlyError")))
                            "COMPLETED" -> {
                                val resultJson = data.getJsonObject("output")
                                val video = resultJson.getJsonObject("video")?:resultJson.getJsonObject("audio")
                                val videoUrl = video.getString("url")
                                if(videoUrl.startsWith("http")) {
                                    haveUrl(videoUrl)
                                    downloadFromUrl(videoUrl, taskId, fileChoose, onError, handler)
                                }else{
                                    onError(Throwable("no url found"))
                                }
                            }
                        }

                    } else {
                        val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                        println(errorMessage)
                        // onError(Exception(errorMessage + response.statusCode()))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .onFailure { e ->
                e.printStackTrace()
            }
    }

    override fun updatePrices() {
        AiUsageScope.lowPriorityScope.launch {
            try {



                /*prices["image"]=json.get("image").asJsonObject.get("cost").asString.toDouble()
                val models = json.get("video").asJsonObject
                VideoGenerationModel.entries.forEach {vgm->
                    val model = models.get(vgm.modelNames[this@AiGate]).asJsonObject

                    vgm.durations.forEach { duration ->
                        try{
                            prices["video_${vgm.name}_$duration"]=model.get(duration).asString.toDouble()
                        }catch (e:Throwable){
                            e.printStackTrace()
                        }
                    }


                }

                prices["sound"]=json.get("sound").asJsonObject.get("cost").asString.toDouble()
                prices["voice"]=json.get("voice").asJsonObject.get("cost").asString.toDouble()*/
            }catch (e:Throwable){
                e.printStackTrace()
            }
        }
    }
}

data object Openrouter : ApiProvider(){
    override val accountUrl: String = "https://openrouter.ai/settings/keys"
    override val token:String
    get() {return AiUsageConfig.openRouterToken?:""}
    val url = "https://openrouter.ai/api/v1/chat/completions"
    val maxTimeMs = 500_000L
    override fun startUpdateBalance() {
        updateJob =AiUsageScope.lowPriorityScope.launch {
            try {
                val response = webClient.getAbs("https://openrouter.ai/api/v1/credits")
                    .putHeader("Authorization", "Bearer $token")
                    .send()
                    .await()
                val json = response.bodyAsJsonObject()
                val amount =
                    json.getJsonObject("data").getString("total_credits").toDouble() - json.getJsonObject("data")
                        .getString("total_usage").toDouble()
                balance = amount.toFloat()
            } catch (e: Throwable) {
                AiUsageLoggers.exceptions.log(e)
            } finally {
                updateJob = null
            }
        }
    }
    override fun createText(
        prompt: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (String) -> Unit) {
        AiUsageScope.scope.launch {
           // println("🚀 start request to OpenRouter")
           // println("📝 Prompt: $prompt")

            val body = JsonObject()
                .put("model", getTextModels().first().modelNames[this@Openrouter])
                .put("messages", JsonArray()
                    .add(
                        JsonObject()
                            .put("role", "user")
                            .put("content", JsonArray()
                                .add(
                                    JsonObject()
                                        .put("type", "text")
                                        .put("text", prompt)
                                )
                            )
                    )
                )


            //println("🌐 URL: $url")

            try {
               // println("⏳ Sending request...")

                val response = webClient.postAbs(url)
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .timeout(maxTimeMs, TimeUnit.MILLISECONDS)
                    .coAwait()

               // println("✅ Received response, status: ${response.statusCode()}")

                if (response.statusCode() == 200) {
                    val responseBody = response.bodyAsString()
                   // println("📥 Response ")

                    val json = JsonObject(responseBody)
                    val choices = json.getJsonArray("choices")

                    if (choices != null && choices.size() > 0) {
                        val message = choices.getJsonObject(0).getJsonObject("message")
                        val content = message.getString("content")
                        val result = content ?: "No content in response"
                      //  println("🎯 Extracted content")
                        handler(result)
                    } else {
                        onError( RuntimeException("No choices in response: $responseBody"))
                    }
                } else {
                    val errorBody = response.bodyAsString()
                    println("❌ HTTP Error ${response.statusCode()}: $errorBody")
                    onError(RuntimeException("HTTP ${response.statusCode()}: $errorBody"))
                }
            } catch (e: Exception) {
                println("💥 Request failed: ${e.message}")
                e.printStackTrace()
                onError( e)
            } finally {
              //  println("🏁 end request")
            }
        }
    }
    val httpClient = vertx.createHttpClient()
    override fun chat(
        chat: Chat,
        onError: (Throwable?) -> Unit,
        handler: (ChatMessage) -> Unit
    ) {
        AiUsageScope.scope.launch {
            // Подготавливаем тело запроса
            val messages = JsonArray().apply {
                chat.messages.dropLast(1).forEach { msg ->
                    add(JsonObject()
                        .put("role", if (msg.isUser) "user" else "assistant")
                        .put("content", msg.text)
                    )
                }
            }

            val body = JsonObject()
                .put("model", TextGenerationModel.Free.modelNames[this@Openrouter])
                .put("messages", messages)
                .put("stream", true)  // Включаем потоковый режим

            try {
                // Отправляем запрос

                val options = RequestOptions()
                    .setMethod(HttpMethod.POST)
                    .setTimeout(maxTimeMs)
                    .setIdleTimeout(maxTimeMs)
                    .setConnectTimeout(maxTimeMs)
                    .setHost("openrouter.ai")
                    .setPort(443)
                    .setSsl(true)
                    .setURI("/api/v1/chat/completions")

                    .putHeader("Authorization", "Bearer "+Openrouter.token)
                    .putHeader("Content-Type", "application/json")
                /*val response = webClient.postAbs(url)
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    .timeout(maxTimeMs)
                    .sendJsonObject(body)
                    .coAwait()*/
                val httpRequest = httpClient!!.request(options).coAwait()

                val response = httpRequest!!.send(body.toBuffer()).coAwait()
                if (response.statusCode() == 200) {
                    // Обрабатываем потоковый ответ
                    processSSEStream(response, handler, onError, chat.messages.last())
                } else {

                    println("❌ HTTP Error ${response.statusCode()}")
                    onError(RuntimeException("HTTP ${response.statusCode()}"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("💥 Request failed: ${e.message}")
                onError(e)
            }
        }
    }

    private suspend fun processSSEStream(
        response: HttpClientResponse,
        handler: (ChatMessage) -> Unit,
        onError: (Throwable?) -> Unit,
        chatMessage: ChatMessage
    ) {
        var buffer = ""
        val accumulatedResult = StringBuilder()
        val accumulatedThinking = StringBuilder()
        response.handler { chunk: Buffer ->
            buffer += chunk.toString()
            while (true) {
                val lineEnd = buffer.indexOf("\n")
                if (lineEnd == -1) break
                val line = buffer.substring(0, lineEnd)
                buffer = buffer.substring(lineEnd + 1)
                if (line.startsWith("data: ")) {
                    val jsonStr = line.substring(6).trim()
                    println(jsonStr)
                    if (jsonStr != "[DONE]") {
                        try {
                            val json = JsonObject(jsonStr)
                            json.getJsonArray("choices")?.forEach { choice ->
                                val delta = (choice as JsonObject).getJsonObject("delta")
                                delta.getString("content")?.let { content ->
                                    if (content.isNotEmpty()) {
                                        accumulatedResult.append(content)
                                        handler(chatMessage.copy(text = accumulatedResult.toString()))
                                    }


                                }
                                delta.getString("reasoning")?.let { content ->
                                    print(content)
                                    accumulatedThinking.append(content)
                                    //thinkingHandler(content)
                                }
                            }
                            json.getJsonObject("usage")?.let {
                                // gptRequestResult.promptTokensUsed = it.getInteger("prompt_tokens")
                                // gptRequestResult.completionTokensUsed = it.getInteger("completion_tokens")
                            }

                        } catch (e: Exception) {
                            println("\nError parsing chunk: ${e.message}")
                        }
                    } else {
                        handler(chatMessage.copy(text = accumulatedResult.toString(), isCompleted = true))
                    }
                }
            }


        }
    }


}
data object AI_ML :ApiProvider(){
    override val accountUrl: String = "https://aimlapi.com/api-key"
    override val token: String
        get() = AiUsageConfig.aiMlToken ?: ""

    override fun startUpdateBalance() {
        updateJob = AiUsageScope.lowPriorityScope.launch {
            try {
                println("Start update balance for AI_ML")
                val response = webClient.getAbs("https://api.aimlapi.com/v1/billing/balance")
                    .putHeader("Authorization", "Bearer $token")
                    .send()
                    .await()

                println("✅ Received balance response, status: ${response.statusCode()}")

                if (response.statusCode() == 200) {
                    val responseBody = response.bodyAsJsonObject()
                    val balanceAmount = responseBody.getDouble("balance")?.toFloat()
                    balance = balanceAmount ?: 0f
                } else {
                    println("❌ Balance check failed: ${response.statusCode()} - ${response.bodyAsString()}")
                }
            } catch (e: Exception) {
                println("💥 Balance update failed: ${e.message}")
                e.printStackTrace()
            } finally {
                updateJob = null
            }
        }
    }

    override fun createText(
        prompt: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (String) -> Unit) {
        AiUsageScope.scope.launch {
            try {
                val body = JsonObject().apply {
                    put("model", getTextModels().first().modelNames[this@AI_ML])
                    put("messages", JsonArray()
                        .add(JsonObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    )
                }

                webClient.postAbs("https://api.aimlapi.com/v1/chat/completions")
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode() == 200) {
                                val json = response.bodyAsJsonObject()
                                val choices = json.getJsonArray("choices")
                                if (choices != null && choices.size() > 0) {
                                    val message = choices.getJsonObject(0).getJsonObject("message")
                                    val content = message.getString("content")
                                    handler(content ?: "")
                                } else {
                                    onError(Exception("No choices in response"))
                                }
                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error: ${error.message}"))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    override fun createImageWithImageUrls(
        prompt: String,
        imagesUrls: List<String>?,
        format: String?,
        fileBase: String,
        haveTaskId: (String) -> Unit,
        onError: (Throwable) -> Unit,
        handler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            try {
                val fileChoose: () -> File = {
                    getNextNoExistFile("images", fileBase, imageExtension)
                }
                val model =  if(imagesUrls?.isNotEmpty() == true){
                    ImageGenerationModel.NanoBananaEdit
                }else{

                    ImageGenerationModel.NanoBanana
                }
                val body = JsonObject().apply {
                    put("prompt", prompt)
                    put("model", model.modelNames[AI_ML])
                    format?.let { put("aspect_ratio", it) }
                    put("num_images", 1)
                    if (imagesUrls?.isNotEmpty() == true){
                        put("image_urls", JsonArray().also {
                            imagesUrls.forEach { imageUrl -> it.add(imageUrl) }
                        })
                    }
                }

                webClient.postAbs("https://api.aimlapi.com/v1/images/generations")
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    /*.proxy(ProxyOptions()
                        .setType(ProxyType.SOCKS5)
                        .setHost("192.168.0.164")
                        .setPort(4401)
                    )*/
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode() == 200) {
                                val json = response.bodyAsJsonObject()
                                println(json.toString())
                                val data = json.getJsonArray("data")
                                if (data != null && data.size() > 0) {
                                    val imageUrl = data.getJsonObject(0).getString("url")
                                    if (imageUrl != null && imageUrl.startsWith("http")) {
                                        downloadFromUrl(imageUrl, "aimlapi_image_${Random.nextUInt()}", fileChoose, onError, handler)
                                    } else {
                                        onError(Exception("Invalid image URL in response"))
                                    }
                                } else {
                                    onError(Exception("No image data in response"))
                                }
                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error: ${error.message}"))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }


    override fun createVideoWithImageFiles(
        videoGenerationModel: VideoGenerationModel,
        prompt: String,
        images: List<File>?,
        duration: String,
        aspectRatio :String,
        resolution: String,
        fileBase: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable) -> Unit,
        resultHandler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            try {
                val fileChoose: () -> File = {
                    getNextNoExistFile("video", fileBase, videoExtension)
                }

                // Для aimlapi.com используем стандартный видео генератор
                val body = JsonObject().apply {
                    put("prompt", prompt)
                    put("model", "stable-video-diffusion")
                    duration.toIntOrNull()?.let { put("duration_seconds", it) }
                    put("resolution", resolution)
                    // Загрузка изображения, если есть
                    images?.let {
                        if(it.size>1){
                            put("image_urls", JsonArray().also {array->
                                images.forEach {
                                    uploadFun?.invoke(it)?.let { imageUrl ->
                                        array.add(imageUrl)
                                    }
                                }
                            })

                        }else{
                            uploadFun?.invoke(it.first())?.let { imageUrl ->
                                put("image_url", imageUrl)
                            }
                        }

                    }
                }

                webClient.postAbs("https://api.aimlapi.com/v1/videos/generations")
                    .putHeader("Authorization", "Bearer $token")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .onSuccess { response ->
                        try {
                            if (response.statusCode() == 200) {
                                val json = response.bodyAsJsonObject()
                                val taskId = json.getString("task_id")
                                if (taskId != null) {
                                    haveTaskId(taskId)
                                    // Запускаем проверку статуса задачи
                                    awaitAndDownloadVideo(taskId, fileChoose, onError, resultHandler)
                                } else {
                                    onError(Exception("No task ID in response"))
                                }
                            } else {
                                val errorMessage = "HTTP ${response.statusCode()}: ${response.bodyAsString()}"
                                onError(Exception(errorMessage))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onError(e)
                        }
                    }
                    .onFailure { error ->
                        onError(Exception("Network error: ${error.message}"))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    private fun awaitAndDownloadVideo(
        taskId: String,
        fileChoose: () -> File,
        onError: (Throwable) -> Unit,
        resultHandler: (File) -> Unit
    ) {
        AiUsageScope.lowPriorityScope.launch {
            var repeats = 60
            var resultFile: File? = null

            while (resultFile == null && --repeats >= 0) {
                try {
                    val statusResponse = webClient.getAbs("https://api.aimlapi.com/v1/videos/status/$taskId")
                        .putHeader("Authorization", "Bearer $token")
                        .send()
                        .await()

                    if (statusResponse.statusCode() == 200) {
                        val json = statusResponse.bodyAsJsonObject()
                        val status = json.getString("status")
                        when (status) {
                            "completed" -> {
                                val videoUrl = json.getString("video_url")
                                if (videoUrl != null && videoUrl.startsWith("http")) {
                                    downloadFromUrl(videoUrl, taskId, fileChoose, onError) { file ->
                                        resultFile = file
                                        resultHandler(file)
                                    }
                                    break
                                }
                            }
                            "failed" -> {
                                val errorMsg = json.getString("error_message") ?: "Video generation failed"
                                onError(Exception(errorMsg))
                                break
                            }
                            else -> {
                                // still processing
                                delay(10_000)
                            }
                        }
                    } else {
                        delay(10_000)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(10_000)
                }
            }

            if (resultFile == null && repeats < 0) {
                onError(Exception("Video generation timeout"))
            }
        }
    }

    override val uploadFun: (suspend (File) -> String?)? = { file ->
        try {
            println("Start upload to AI_ML")
            val buffer = Buffer.buffer(file.readBytes())
            val requestBody = io.vertx.ext.web.multipart.MultipartForm.create().apply {
                binaryFileUpload("file", file.name, buffer, file.getMimeType())
            }

            val response = webClient.postAbs("https://api.aimlapi.com/v1/files/upload")
                .putHeader("Authorization", "Bearer $token")
                .sendMultipartForm(requestBody)
                .await()

            println("✅ Received upload response, status: ${response.statusCode()}")

            if (response.statusCode() == 200) {
                val responseBody = response.bodyAsJsonObject()
                val fileUrl = responseBody.getString("file_url")
                if (fileUrl != null && fileUrl.startsWith("http")) {
                    println("File uploaded to AI_ML: $fileUrl")
                    fileUrl
                } else {
                    println("Invalid file URL in response")
                    null
                }
            } else {
                println("Upload failed: ${response.statusCode()} - ${response.bodyAsString()}")
                null
            }
        } catch (e: Exception) {
            println("💥 Upload failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override fun updatePrices() {
        // Для AI_ML можно загрузить цены с их API или использовать фиксированные
        AiUsageScope.lowPriorityScope.launch {
            try {
                // Примерные цены для AI_ML (нужно уточнить по документации)
                prices["text"] = 0.002 // за 1K токенов
                prices["image"] = 0.016 // за изображение DALL-E 3
                prices["video"] = 0.1 // за секунду видео
                // Можно добавить запрос к API для получения актуальных цен
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
data object AiGate : ApiProvider(){
    override val accountUrl: String = "https://api.ai-gate.one/account"
   // val apiUrl = "https://api.ai-gate.one/api/v1/"
   val apiUrl = "https://api.ai-gate.one/api/v1/"
    override val token:String
        get() {return AiUsageConfig.aigateToken?:""}

    override val uploadFun: (suspend (File) -> String?)={file->
        try {
            println("Start upload to AiGate")
            val buffer = Buffer.buffer(file.readBytes())
            val requestBody = io.vertx.ext.web.multipart.MultipartForm.create().apply {
                binaryFileUpload(file.name, file.name, buffer, file.getMimeType())

            }
            val response = webClient.postAbs("https://api.ai-gate.one/tmp/upload")
                .putHeader("X-API-Key", token)
                .sendMultipartForm(requestBody)
                .await()

            println("✅ Received response, status: ${response.statusCode()}")

            if (response.statusCode() == 200) {
                val responseBody = response.bodyAsJsonObject()
                val url = responseBody.getString("url")
                if(url?.startsWith("http") == true){
                    println(url)
                    url
                }else{
                    null
                }
            }else{
                println("Error"+response.bodyAsString())
                null
            }
        } catch (e: Exception) {
            println("💥 Request failed: ${e.message}")
            e.printStackTrace()
            null
        }

    }
    override fun startUpdateBalance() {
        updateJob =AiUsageScope.lowPriorityScope.launch {
            try {
                try {
                    println("Start update balance for AiGate")
                    val response = webClient.getAbs(apiUrl +"balance/"+ token)
                        .putHeader("Content-Type", "application/json")
                        .send()
                        .await()

                    println("✅ Received UpdateBalance response, status: ${response.statusCode()} ${response.bodyAsString()}")

                    if (response.statusCode() == 200) {
                        val responseBody = response.bodyAsString()
                        balance = responseBody.toFloat()
                    }else{
                        println("Error"+response.bodyAsString())
                    }
                } catch (e: Exception) {
                    println("💥 Request failed: ${e.message}")
                    e.printStackTrace()
                }
            } catch (e: Throwable) {
                AiUsageLoggers.exceptions.log(e)
            } finally {
                updateJob = null
            }
        }
    }
    override fun createText(
        prompt: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (String) -> Unit) {
        AiUsageScope.scope.launch {


            val body = JsonObject()
                .put("apiKey", token.ifEmpty { "FreeScenarioWriter" })
                .put("text",prompt)



            try {

                val response = webClient.postAbs(apiUrl +"text")
                    .putHeader("X-API-Key", token.ifEmpty { "FreeScenarioWriter" })
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .timeout(maxTimeMs, TimeUnit.MILLISECONDS)
                    .coAwait()

                 println("✅ Received response, status: ${response.statusCode()}")

                if (response.statusCode() == 200) {
                    val responseBody = response.bodyAsJsonObject()
                    val taskId = responseBody.getString("taskId")
                    println("✅ Received taskId: ${taskId}")
                    haveTaskId(taskId)

                    var repeats = 100
                    var resultText:String? = null
                    delay(3_000)
                    while (resultText == null && --repeats >= 0) {
                        try {
                            val resultResponse = webClient.getAbs(apiUrl +"result/$taskId")
                                .putHeader("X-API-Key", token.ifEmpty { "FreeScenarioWriter" })
                                .putHeader("Content-Type", "application/json")
                                .send()
                                .coAwait()
                            //println(resultResponse.bodyAsString())
                            val resultResponseJson = resultResponse.bodyAsJsonObject()
                            if(resultResponseJson.getString("type") == "Fail"){
                                repeats=0
                            }
                            resultText = resultResponseJson.getString("data")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        delay(3_000)
                    }
                    if(resultText!=null){
                        handler(resultText)
                    }else{
                        onError(Throwable("Request failed"))
                    }
                } else {
                    val errorBody = response.bodyAsString()
                    println("❌ HTTP Error ${response.statusCode()}: $errorBody")
                    onError(RuntimeException("HTTP ${response.statusCode()}: $errorBody"))
                }
            } catch (e: Exception) {
                println("💥 Request failed: ${e.message}")
                e.printStackTrace()
                onError( e)
            } finally {
                //  println("🏁 end request")
            }
        }
    }

    override fun createImageWithImageFiles(
        prompt: String,
        images: List<File>?,
        format: String?,
        fileBase: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable) -> Unit,
        handler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            val body = JsonObject()
                .put("apiKey", token)
                .put("prompt",prompt)
                .put("aspectRatio",format?:"auto")
                .put("imageUrls", JsonArray(images?.mapNotNull { uploadFun(it) }?: emptyList<String>()))
            try {
                val fileChoose:(()->File) = {
                    getNextNoExistFile("images",fileBase, imageExtension)
                }
                val response = webClient.postAbs(apiUrl +"image")
                    .putHeader("X-API-Key", token)
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .timeout(maxTimeMs, TimeUnit.MILLISECONDS)
                    .coAwait()

                println("✅ Received response, status: ${response.statusCode()}")

                if (response.statusCode() == 200) {
                    val responseBody = response.bodyAsJsonObject()
                    val taskId = responseBody.getString("taskId")
                    println("✅ Received taskId: ${taskId}")
                    haveTaskId(taskId)

                    var repeats = 100
                    var resultUrl:String? = null
                    delay(3_000)
                    while (resultUrl == null && --repeats >= 0) {
                        try {
                            val resultResponse = webClient.getAbs(apiUrl +"result/$taskId")
                                .putHeader("X-API-Key", token)
                                .putHeader("Content-Type", "application/json")
                                .send()
                                .coAwait()
                            println(resultResponse.bodyAsString())
                            val resultResponseJson = resultResponse.bodyAsJsonObject()
                            if(resultResponseJson.getString("type") == "Fail"){
                                repeats=0
                            }
                            resultUrl = resultResponseJson.getString("url")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        delay(3_000)
                    }
                    if(resultUrl!=null){
                        downloadFromUrl(resultUrl,taskId, fileChoose, onError){saveFile->
                            println("saveFile:$saveFile exist=${saveFile.exists()}")
                            handler(saveFile)
                        }
                    }else{
                        onError(Throwable("Request failed"))
                    }
                } else {
                    val errorBody = response.bodyAsString()
                    println("❌ HTTP Error ${response.statusCode()}: $errorBody")
                    onError(RuntimeException("HTTP ${response.statusCode()}: $errorBody"))
                }
            } catch (e: Exception) {
                println("💥 Request failed: ${e.message}")
                e.printStackTrace()
                onError( e)
            } finally {
                //  println("🏁 end request")
            }
        }
    }

    override fun createSoundEffect(
        soundEffectGenerationModel: SoundEffectGenerationModel,
        prompt: String,
        durationMs: Int,
        isLoop: Boolean,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            val body = JsonObject()
                .put("apiKey", token)
                .put("prompt",prompt)
                .put("durationMs",durationMs)
                .put("isLoop",isLoop)

            try {
                val fileChoose:(()->File) = {
                    getNextNoExistFile("sounds", prompt.toTranslit().take(20), audioExtension)
                }
                val response = webClient.postAbs(apiUrl +"sound")
                    .putHeader("X-API-Key", token)
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .timeout(maxTimeMs, TimeUnit.MILLISECONDS)
                    .coAwait()

                println("✅ Received response, status: ${response.statusCode()}")

                if (response.statusCode() == 200) {
                    val responseBody = response.bodyAsJsonObject()
                    val taskId = responseBody.getString("taskId")
                    println("✅ Received taskId: ${taskId}")
                    haveTaskId(taskId)

                    var repeats = 100
                    var resultUrl:String? = null
                    delay(3_000)
                    while (resultUrl == null && --repeats >= 0) {
                        try {
                            val resultResponse = webClient.getAbs(apiUrl +"result/$taskId")
                                .putHeader("X-API-Key", token)
                                .putHeader("Content-Type", "application/json")
                                .send()
                                .coAwait()
                            println(resultResponse.bodyAsString())
                            val resultResponseJson = resultResponse.bodyAsJsonObject()
                            if(resultResponseJson.getString("type") == "Fail"){
                                repeats=0
                            }
                            val status = resultResponseJson.getString("type")
                            if(status == "Fail"){
                                repeats = 0
                            }
                            resultUrl = resultResponseJson.getString("url")

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        delay(3_000)
                    }
                    if(resultUrl!=null){
                        downloadFromUrl(resultUrl,taskId, fileChoose, onError, handler)
                    }else{
                        onError(Throwable("Request failed"))
                    }
                } else {
                    val errorBody = response.bodyAsString()
                    println("❌ HTTP Error ${response.statusCode()}: $errorBody")
                    onError(RuntimeException("HTTP ${response.statusCode()}: $errorBody"))
                }
            } catch (e: Exception) {
                println("💥 Request failed: ${e.message}")
                e.printStackTrace()
                onError( e)
            } finally {
                //  println("🏁 end request")
            }
        }
    }
    override fun voiceGeneration(
        voiceGenerationModel: VoiceGenerationModel,
        voice: ElevenLabsVoices,
        text: String,
        stability: Float,
        similarity_boost: Float,
        style:Float,
        speed: Float,
        previousText: String?,
        nextText: String?,
        fileBase: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable?) -> Unit,
        handler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            val body = JsonObject()
                .put("apiKey", token)
                .put("name",voice.name)
                .put("text",text)
                .put("stability",stability)
                .put("similarity_boost",similarity_boost)
                .put("style",style)
                .put("speed",speed)
                .put("previousText",previousText)
                .put("nextText",nextText)
            try {
                val fileChoose:(()->File) = {
                    getNextNoExistFile("sounds", fileBase, audioExtension)
                }
                val response = webClient.postAbs(apiUrl +"voice")
                    .putHeader("X-API-Key", token)
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .timeout(maxTimeMs, TimeUnit.MILLISECONDS)
                    .coAwait()

                println("✅ Received response, status: ${response.statusCode()}")

                if (response.statusCode() == 200) {
                    val responseBody = response.bodyAsJsonObject()
                    val taskId = responseBody.getString("taskId")
                    println("✅ Received taskId: ${taskId}")
                    haveTaskId(taskId)

                    var repeats = 100
                    var resultUrl:String? = null
                    delay(3_000)
                    while (resultUrl == null && --repeats >= 0) {
                        try {
                            val resultResponse = webClient.getAbs(apiUrl +"result/$taskId")
                                .putHeader("X-API-Key", token)
                                .putHeader("Content-Type", "application/json")
                                .send()
                                .coAwait()
                            println(resultResponse.bodyAsString())
                            val resultResponseJson = resultResponse.bodyAsJsonObject()
                            if(resultResponseJson.getString("type") == "Fail"){
                                repeats=0
                            }
                            resultUrl = resultResponseJson.getString("url")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        delay(3_000)
                    }
                    if(resultUrl!=null){
                        downloadFromUrl(resultUrl,taskId, fileChoose, onError, handler)
                    }else{
                        onError(Throwable("Request failed"))
                    }
                } else {
                    val errorBody = response.bodyAsString()
                    println("❌ HTTP Error ${response.statusCode()}: $errorBody")
                    onError(RuntimeException("HTTP ${response.statusCode()}: $errorBody"))
                }
            } catch (e: Exception) {
                println("💥 Request failed: ${e.message}")
                e.printStackTrace()
                onError( e)
            } finally {
                //  println("🏁 end request")
            }
        }
    }

    override fun createVideoWithImageFiles(
        videoGenerationModel: VideoGenerationModel,
        prompt: String,
        images: List<File>?,
        duration: String,
        aspectRatio :String,
        resolution: String,
        fileBase: String,
        haveTaskId:((String)->Unit),
        onError: (Throwable) -> Unit,
        resultHandler: (File) -> Unit
    ) {
        AiUsageScope.scope.launch {
            val body = JsonObject()
                .put("apiKey", token)
                .put("prompt",prompt)
                .put("modelName",videoGenerationModel.modelNames[AiGate])
                .put("durationSec",duration)
                .put("aspectRatio",aspectRatio)
                .put("resolution",resolution)
                .put("imageUrls",JsonArray().also {array->
                    images?.forEach{
                        array.add(uploadFun(it))
                    }
                })
            try {
                val fileChoose:(()->File) = {
                    getNextNoExistFile("video",  fileBase, videoExtension)
                }
                val response = webClient.postAbs(apiUrl +"video")
                    .putHeader("X-API-Key", token)
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(body)
                    .timeout(maxTimeMs, TimeUnit.MILLISECONDS)
                    .coAwait()

                println("✅ Received response, status: ${response.statusCode()}")

                if (response.statusCode() == 200) {
                    val responseBody = response.bodyAsJsonObject()
                    val taskId = responseBody.getString("taskId")
                    println("✅ Received taskId: ${taskId}")
                    haveTaskId(taskId)

                    var repeats = 200
                    var resultUrl:String? = null
                    delay(3_000)
                    while (resultUrl == null && --repeats >= 0) {
                        try {
                            val resultResponse = webClient.getAbs(apiUrl +"result/$taskId")
                                .putHeader("X-API-Key", token)
                                .putHeader("Content-Type", "application/json")
                                .send()
                                .coAwait()
                            println(resultResponse.bodyAsString())
                            val resultResponseJson = resultResponse.bodyAsJsonObject()
                            if(resultResponseJson.getString("type") == "Fail"){
                                repeats=0
                            }
                            resultUrl = resultResponseJson.getString("url")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        delay(3_000)
                    }
                    if(resultUrl!=null){
                        downloadFromUrl(resultUrl, taskId, fileChoose, onError, resultHandler)
                    }else{
                        onError(Throwable("Request failed"))
                    }
                } else {
                    val errorBody = response.bodyAsString()
                    println("❌ HTTP Error ${response.statusCode()}: $errorBody")
                    onError(RuntimeException("HTTP ${response.statusCode()}: $errorBody"))
                }
            } catch (e: Exception) {
                println("💥 Request failed: ${e.message}")
                e.printStackTrace()
                onError( e)
            } finally {
                //  println("🏁 end request")
            }
        }
    }

    override fun updatePrices() {
        AiUsageScope.lowPriorityScope.launch {
            try {

                val response = webClient.getAbs(apiUrl + "price")
                    .putHeader("Content-Type", "application/json")
                    .send()
                    .timeout(maxTimeMs, TimeUnit.MILLISECONDS)
                    .coAwait()

                println("✅ Received updatePrices response, status: ${response.statusCode()}")
                val json = JsonParser.parseString(response.bodyAsString()).asJsonObject
                prices["text"]=json.get("text").asJsonObject.get("cost").asString.toDouble()
                prices["image"]=json.get("image").asJsonObject.get("cost").asString.toDouble()
                val models = json.get("video").asJsonObject
                VideoGenerationModel.entries.forEach { vgm->
                    val model = models.get(vgm.name)?.asJsonObject
                    vgm.resolutions.forEach {resolution->
                        vgm.durations.forEach { duration ->
                            try{
                                prices["video_${vgm.name}_${resolution}_$duration"]=model?.get(resolution)?.asJsonObject?.get(duration)?.asString?.toDouble()!!
                            }catch (e:Throwable){
                                e.printStackTrace()
                            }
                        }

                    }



                }

                prices["sound"]=json.get("sound").asJsonObject.get("cost").asString.toDouble()
                prices["voice"]=json.get("voice").asJsonObject.get("cost").asString.toDouble()
            }catch (e:Throwable){
                e.printStackTrace()
            }
        }
    }
}
fun downloadFromUrl(videoUrl: String, taskId: String, fileChoose:(()->File), onError: (Throwable) -> Unit, handler: (File) -> Unit) {
    println("start dawnload $videoUrl")
    webClient.getAbs(videoUrl)
        .send()
        .onSuccess { response ->
            if (response.statusCode() == 200) {
               AiUsageScope.scope.launch {
                   try {
                       val videoData: Buffer = response.bodyAsBuffer()
                       val outputFile = File.createTempFile("file_${taskId}_", ".any")
                       outputFile.writeBytes(videoData.bytes)
                       println("Video downloaded successfully: ${outputFile.absolutePath}")
                       val targetFile = fileAccessMutex.withLock {
                           Files.move(outputFile.toPath(),fileChoose().toPath())
                       }
                       handler(targetFile.toFile())

                   } catch (e: Exception) {
                       e.printStackTrace()
                       onError(Exception("Failed to save video file: ${e.message}"))
                   }
               }
            } else {
                onError(Exception("Failed to download video: HTTP ${response.statusCode()}"))
            }
        }
        .onFailure { error ->
            onError(Exception("Failed to download video from URL: ${error.message}"))
        }
}
