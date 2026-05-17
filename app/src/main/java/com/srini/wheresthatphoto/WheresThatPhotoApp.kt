package com.srini.wheresthatphoto

import android.app.Application
import com.srini.wheresthatphoto.data.AppDatabase
import com.srini.wheresthatphoto.indexing.FaceClusterer
import com.srini.wheresthatphoto.indexing.Indexer
import com.srini.wheresthatphoto.media.MediaStoreRepository
import com.srini.wheresthatphoto.media.PhotoMetadataExtractor
import com.srini.wheresthatphoto.ml.ClipEncoder
import com.srini.wheresthatphoto.ml.FaceDetectorEngine
import com.srini.wheresthatphoto.ml.GemmaCaptioner
import com.srini.wheresthatphoto.ml.PetRegionDetector
import com.srini.wheresthatphoto.ml.TextEncoder
import com.srini.wheresthatphoto.people.PeopleRepository
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
    private val photoMetadataExtractor = PhotoMetadataExtractor(app)
    private val faceDetector = FaceDetectorEngine(app)
    private val petRegionDetector = PetRegionDetector(app)
    private val faceClusterer = FaceClusterer(app, clipEncoder, database.identityDao())

    val indexer: Indexer = Indexer(
        appContext = app,
        mediaStoreRepository = mediaStoreRepository,
        clipEncoder = clipEncoder,
        textEncoder = textEncoder,
        gemmaCaptioner = gemmaCaptioner,
        photoMetadataExtractor = photoMetadataExtractor,
        faceDetector = faceDetector,
        petRegionDetector = petRegionDetector,
        faceClusterer = faceClusterer,
        database = database
    )

    val searchRepository: SearchRepository = SearchRepository(
        database = database,
        clipEncoder = clipEncoder,
        textEncoder = textEncoder,
        gemmaCaptioner = gemmaCaptioner
    )

    val peopleRepository: PeopleRepository = PeopleRepository(
        database = database,
        context = app
    )
}
