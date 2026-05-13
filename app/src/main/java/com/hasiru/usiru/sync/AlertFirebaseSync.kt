package com.hasiru.usiru.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.hasiru.usiru.data.AlertDao
import com.hasiru.usiru.data.EcologicalAlert
import kotlinx.coroutines.tasks.await

class AlertFirebaseSync(
    private val dao: AlertDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun pushUnsynced(): Int {
        val pending = dao.unsynced()
        if (pending.isEmpty()) return 0

        val batch = firestore.batch()
        pending.forEach { alert ->
            val doc = firestore.collection("ecological_alerts").document(alert.id.toString())
            batch.set(doc, alert.toCloudMap())
        }
        batch.commit().await()
        dao.markSynced(pending.map { it.id })
        return pending.size
    }

    private fun EcologicalAlert.toCloudMap(): Map<String, Any?> = mapOf(
        "type" to type.name,
        "status" to status.name,
        "latitude" to latitude,
        "longitude" to longitude,
        "accuracyMeters" to accuracyMeters,
        "photoPathOnDevice" to photoPath,
        "notes" to notes,
        "createdAt" to createdAt
    )
}
