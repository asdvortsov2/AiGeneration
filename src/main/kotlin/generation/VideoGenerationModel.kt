package generation

enum class VideoGenerationModel(
    val modelNames:Map<ApiProvider,String>,
    val durations:List<String>,
    val resolutions:List<String>,
    val maxPromptLength:Int = 10000,
    val allowEndFrame:Boolean = false,
) {
    Sora2(
        modelNames = mapOf(
            Kie to "sora-2-image-to-video",
            AiGate to "Sora2"
        ),
        durations = listOf("10", "15"),
        resolutions = listOf("1080p")
    ),
    Wan2_6(
        modelNames = mapOf(
            Kie to "wan/2-6-image-to-video",
            //NanoGpt to "wan-wavespeed-25",
            AiGate to "Wan2_6"
        ),
        durations = listOf("5", "10"),
        resolutions = listOf("1080p"),
        maxPromptLength = 5000
    ),
    Kling_2_5_Pro(
        modelNames = mapOf(
            Kie to "kling/v2-5-turbo-image-to-video-pro",
            AiGate to "Kling_2_5_Pro"
        ),
        durations = listOf("5", "10"),
        resolutions = listOf("1080p"),
        maxPromptLength = 1000
    ),
    Kling2_6(
        modelNames = mapOf(
            Kie to "kling-2.6/image-to-video",
            AiGate to "Kling2_6"
        ),
        durations = listOf("5", "10"),
        resolutions = listOf("1080p"),
        maxPromptLength = 1000
    ),
    Kling3(
        modelNames = mapOf(
            Kie to "kling-3.0/video",
            AiGate to "Kling3",
        ),
        durations = listOf("5", "8", "10", "13"),
        resolutions = listOf("1080p"),
        maxPromptLength = 1000,
        allowEndFrame=true
    ),
    Seedance1_5pro(
        modelNames = mapOf(
            Kie to "bytedance/seedance-1.5-pro",
        ),
        durations = listOf("4","8", "12"),
        resolutions = listOf("480p","720p", "1080p")
    ),
    Veo3_1Fast(
        modelNames = mapOf(
            Kie to "veo3_fast",
            AiGate to "Veo3_1Fast"
        ),
        durations = listOf("8"),
        resolutions = listOf("720p"),
       allowEndFrame=true
    ),
    Veo3_1_Quality(
        modelNames = mapOf(
            Kie to "veo3",
            AiGate to "Veo3_1_Quality"
        ),
        durations = listOf("8"),
        resolutions =  listOf( "1088p"),
        allowEndFrame=true
    ),
    Hailuo2_3(
        modelNames = mapOf(
            Kie to "hailuo/2-3-image-to-video-pro",
            AiGate to "Hailuo2_3"
        ),
        durations = listOf("6", "10"),
        resolutions = listOf("768p", "1080p")
    ),
    Grok_Imagine(
        modelNames = mapOf(
            Kie to "grok-imagine/image-to-video",
            AiGate to "Grok_Imagine"
        ),
        durations = listOf("6", "10"),
        resolutions = listOf("480p", "720p"),
        maxPromptLength = 5000

    )
}
