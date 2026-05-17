package com.srini.wheresthatphoto.people

import android.content.Context
import com.srini.wheresthatphoto.data.AppDatabase
import com.srini.wheresthatphoto.data.FaceThumbnailStore
import com.srini.wheresthatphoto.data.IdentityKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IdentitySummary(
    val identityId: String,
    val kind: IdentityKind,
    val displayName: String?,
    val photoCount: Int,
    val thumbnailPath: String?
)

class PeopleRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    suspend fun loadIdentities(limitPerSection: Int = 12): Pair<List<IdentitySummary>, List<IdentitySummary>> =
        withContext(Dispatchers.IO) {
            val dao = database.identityDao()
            val people = dao.getIdentitiesForPeopleTab(IdentityKind.PERSON.toStorage(), limitPerSection)
                .map { it.toSummary() }
            val pets = dao.getIdentitiesForPeopleTab(IdentityKind.PET.toStorage(), limitPerSection)
                .map { it.toSummary() }
            people to pets
        }

    suspend fun nameIdentity(identityId: String, displayName: String, kind: IdentityKind) =
        withContext(Dispatchers.IO) {
            val trimmed = displayName.trim()
            require(trimmed.isNotEmpty()) { "Name cannot be empty" }
            database.identityDao().setIdentityName(identityId, trimmed, kind.toStorage())
        }

    suspend fun clearThumbnails() = withContext(Dispatchers.IO) {
        FaceThumbnailStore.deleteAll(context)
    }

    private fun com.srini.wheresthatphoto.data.IdentityTabRow.toSummary() = IdentitySummary(
        identityId = identityId,
        kind = IdentityKind.fromStorage(kind),
        displayName = displayName,
        photoCount = photoCount,
        thumbnailPath = thumbnailPath
    )
}
