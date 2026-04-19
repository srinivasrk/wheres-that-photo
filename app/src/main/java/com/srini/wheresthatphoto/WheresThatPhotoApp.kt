package com.srini.wheresthatphoto

import android.app.Application
import com.srini.wheresthatphoto.data.AppDatabase
import com.srini.wheresthatphoto.indexing.Indexer
import com.srini.wheresthatphoto.media.MediaStoreRepository
import com.srini.wheresthatphoto.ml.ClipEncoder
import com.srini.wheresthatphoto.ml.GemmaCaptioner
import com.srini.wheresthatphoto.ml.TextEncoder
import com.srini.wheresthatphoto.search.SearchRepository

class WheresThatPhotoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val database = AppDatabase.create(app)
    private val mediaStoreRepository = MediaStoreRepository(app)
    private val clipEncoder = ClipEncoder(app)
    private val textEncoder = TextEncoder(app)
    private val gemmaCaptioner = GemmaCaptioner(app)

    val indexer: Indexer = Indexer(
        mediaStoreRepository = mediaStoreRepository,
        clipEncoder = clipEncoder,
        textEncoder = textEncoder,
        gemmaCaptioner = gemmaCaptioner,
        database = database
    )

    val searchRepository: SearchRepository = SearchRepository(
        database = database,
        clipEncoder = clipEncoder,
        textEncoder = textEncoder,
        gemmaCaptioner = gemmaCaptioner
    )
}
