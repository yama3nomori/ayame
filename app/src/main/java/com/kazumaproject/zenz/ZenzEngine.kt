package com.kazumaproject.zenz

object ZenzEngine {
    fun initModel(modelPath: String) {}
    fun setRuntimeConfig(
        nCtx: Int,
        nThreads: Int
    ) {}
    fun generate(
        prompt: String,
        maxTokens: Int
    ): String = ""

    fun generateWithContext(
        leftContext: String,
        input: String,
        maxTokens: Int
    ): String = ""

    fun generateWithContextAndConditions(
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        input: String,
        maxTokens: Int
    ): String = ""
}
