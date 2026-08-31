package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.app.Application
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.TamachiRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreloadEntryPoint {
    fun getKanaKanjiEngine(): KanaKanjiEngine
    fun getAppDatabase(): AppDatabase
    fun getLearnRepository(): LearnRepository
    fun getUserDictionaryRepository(): UserDictionaryRepository
    fun getTamachiRepository(): TamachiRepository
    fun getRomajiMapRepository(): RomajiMapRepository
}

@HiltAndroidApp
class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        val dexOutputDir: File = codeCacheDir
        dexOutputDir.setReadOnly()

        // アプリケーション起動時にバックグラウンドで全辞書・データベースを先行プリロード（ウォームアップ）
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                Timber.d("Starting background pre-warming of dictionaries and databases...")
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@Application,
                    PreloadEntryPoint::class.java
                )

                // 1. KanaKanjiEngine（Mozc標準システム辞書群のZIP解凍・LOUDS展開）を先行初期化
                val engine = entryPoint.getKanaKanjiEngine()
                Timber.d("KanaKanjiEngine pre-warmed: $engine")

                // 2. Room DBおよび各リポジトリの初期化とキャッシュ展開
                val db = entryPoint.getAppDatabase()
                val learnRepo = entryPoint.getLearnRepository()
                val userDictRepo = entryPoint.getUserDictionaryRepository()
                val tamachiRepo = entryPoint.getTamachiRepository()
                val romajiRepo = entryPoint.getRomajiMapRepository()

                withContext(Dispatchers.IO) {
                    romajiRepo.updateDefaultMap()
                    learnRepo.predictiveSearchByInput("", limit = 1)
                    userDictRepo.searchByReadingPrefixSuspend("", limit = 1)
                }

                Timber.d("Pre-warming of all dictionaries and databases successfully completed.")
            } catch (e: Exception) {
                Timber.e(e, "Error during background pre-warming")
            }
        }
    }
}
