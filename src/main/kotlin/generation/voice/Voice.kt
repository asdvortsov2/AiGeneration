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
            /*VoiceGenerationModel.FishSpeech2 -> {
                val dataFile = File("data/voices/FishSpeech2/$name.mp3")
                if(!dataFile.exists()){
                    dataFile.parentFile.mkdirs()
                    val inputStream = javaClass.classLoader.getResourceAsStream("voices/FishSpeech2/$name.mp3")
                    val out = FileOutputStream(dataFile)
                    if (inputStream != null) {
                        out.write(inputStream.readBytes())
                    }
                }
                return dataFile
            }*/
            //VoiceGenerationModel.Gemini2_5Pro -> throw Exception("not implemented 87986023495")
        }
    }
    fun getResourceFile(path: String): File {
        val resource = javaClass.classLoader.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")
        return File(resource.toURI())
    }
}
