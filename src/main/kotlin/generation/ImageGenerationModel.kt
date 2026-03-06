package generation

enum class ImageGenerationModel(
    val modelNames:Map<ApiProvider,String>) {
    NanoBananaEdit(
        modelNames = mapOf(
            Kie to "google/nano-banana-edit",
            NanoGpt to "nano-banana-edit",
            AiGate to "",
            AI_ML to "google/gemini-2.5-flash-image-edit"
        ),
    ),
    NanoBanana(
        modelNames = mapOf(
            Kie to "google/nano-banana",
            NanoGpt to "nano-banana",
            AiGate to "",
            AI_ML to "google/gemini-2.5-flash-image"
        ),
    ),
}
