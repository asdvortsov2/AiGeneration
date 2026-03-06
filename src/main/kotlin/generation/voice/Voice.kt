package generation.voice

import generation.VoiceGenerationModel
import java.io.File
import java.io.FileOutputStream

val allVoices = ElevenLabsVoices.entries.map{
    Voice(VoiceGenerationModel.ElevenLabsV3,it.name)
}

data class Voice(
    val generationModel: VoiceGenerationModel,
    val name:String,
){
    fun getExample(): File {
        return when(generationModel){
            VoiceGenerationModel.ElevenLabsV3 -> {
                val dataFile = File("data/voices/ElevenLabs/$name.mp3")
                if(!dataFile.exists()){
                    dataFile.parentFile.mkdirs()
                    val inputStream = javaClass.classLoader.getResourceAsStream("voices/ElevenLabs/$name.mp3")
                    val out = FileOutputStream(dataFile)
                    if (inputStream != null) {
                        out.write(inputStream.readBytes())
                    }
                }
                return dataFile
            }
        }
    }
    fun getResourceFile(path: String): File {
        val resource = javaClass.classLoader.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")
        return File(resource.toURI())
    }
}
