
import java.io.File


object AiUsageLoggers {
    val tasks = GenerationLogger(File(AiUsageConfig.storageFolder.path+ "/logs/tasks.txt"),AiUsageScope.singleThreadScope)
    val exceptions = GenerationLogger(File(AiUsageConfig.storageFolder.path+ "/logs/exceptions.txt"),AiUsageScope.singleThreadScope)

}

