package fr.ziyon.campzone.data.church

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class SDAChurch(
    val id: String,
    val name: String,
    val city: String,
    val country: String,
    val region: String,
    val language: String,
)

data class ChurchGroup(
    val country: String,
    val churches: List<SDAChurch>,
)

/** Filters churches by [query] and groups them by country, sorted for display. */
fun List<SDAChurch>.groupedByCountry(query: String): List<ChurchGroup> {
    val trimmed = query.trim()
    val filtered = if (trimmed.isBlank()) {
        this
    } else {
        filter { church ->
            church.name.contains(trimmed, ignoreCase = true) ||
                church.city.contains(trimmed, ignoreCase = true) ||
                church.region.contains(trimmed, ignoreCase = true) ||
                church.country.contains(trimmed, ignoreCase = true)
        }
    }
    return filtered
        .groupBy { it.country.ifBlank { "Other" } }
        .map { (country, churches) -> ChurchGroup(country = country, churches = churches.sortedBy { it.name }) }
        .sortedBy { it.country }
}

interface ChurchDirectory {
    suspend fun loadChurches(): List<SDAChurch>
}

@Singleton
class ChurchDatabaseRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ChurchDirectory {
    override suspend fun loadChurches(): List<SDAChurch> {
        val churches = firestore
            .collection(ChurchesCollection)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                val data = document.data.orEmpty()
                val name = data.stringValue("name") ?: return@mapNotNull null
                SDAChurch(
                    id = document.id,
                    name = name,
                    city = data.stringValue("city").orEmpty(),
                    country = data.stringValue("country").orEmpty(),
                    region = data.stringValue("region").orEmpty(),
                    language = data.stringValue("language").orEmpty(),
                )
            }
            .sortedWith(compareBy<SDAChurch> { it.country }.thenBy { it.name })

        return churches.ifEmpty { BundledChurchDatabase.all }
    }

    private fun Map<String, Any?>.stringValue(key: String): String? =
        (this[key] as? String)?.trim()?.takeUnless { it.isBlank() }

    private companion object {
        const val ChurchesCollection = "churches"
    }
}

object BundledChurchDatabase {
    val all: List<SDAChurch> = listOf(
        SDAChurch(
            id = "ea-paris-central",
            name = "Eglise Adventiste de Paris-Central",
            city = "Paris",
            country = "France",
            region = "Ile-de-France",
            language = "French",
        ),
        SDAChurch(
            id = "ea-paris-nord",
            name = "Eglise Adventiste de Paris-Nord",
            city = "Paris",
            country = "France",
            region = "Ile-de-France",
            language = "French",
        ),
        SDAChurch(
            id = "ea-geneve",
            name = "Eglise Adventiste de Geneve",
            city = "Geneve",
            country = "Switzerland",
            region = "Geneve",
            language = "French",
        ),
        SDAChurch(
            id = "ea-lausanne",
            name = "Eglise Adventiste de Lausanne",
            city = "Lausanne",
            country = "Switzerland",
            region = "Vaud",
            language = "French",
        ),
        SDAChurch(
            id = "ea-lisboa-central",
            name = "Igreja Adventista Central de Lisboa",
            city = "Lisboa",
            country = "Portugal",
            region = "Lisboa",
            language = "Portuguese",
        ),
        SDAChurch(
            id = "iasd-sao-paulo-central",
            name = "IASD Central de Sao Paulo",
            city = "Sao Paulo",
            country = "Brazil",
            region = "Sao Paulo",
            language = "Portuguese",
        ),
        SDAChurch(
            id = "ea-luxembourg-central",
            name = "Eglise Adventiste de Luxembourg Central",
            city = "Luxembourg",
            country = "Luxembourg",
            region = "Luxembourg",
            language = "French",
        ),
        SDAChurch(
            id = "adventkerk-amsterdam",
            name = "Adventkerk Amsterdam",
            city = "Amsterdam",
            country = "Netherlands",
            region = "Noord-Holland",
            language = "Dutch",
        ),
    )
}
