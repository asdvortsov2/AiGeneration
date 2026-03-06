package generation

enum class TextGenerationModel(
    val modelNames:Map<ApiProvider,String>,
    val maxTextLength:Int = 128000){
        Any(
            modelNames = mapOf(
                Openrouter to "x-ai/grok-4-fast",
                AI_ML to "x-ai/grok-4-fast-non-reasoning",
                AiGate to ""
            ),
            maxTextLength=2_000_000
        ),
        Free(
            modelNames = mapOf(
                Openrouter to "arcee-ai/trinity-large-preview:free",
            ),
            maxTextLength = 130_000
        )

    }
