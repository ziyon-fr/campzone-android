package fr.ziyon.campzone.data.admin

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.core.permissions.UserRole
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Reads the `users` directory and writes role changes for the admin hub.
 *
 * RBAC (`03`): an admin may read/write any `users` doc; a same-church
 * `youth_director`/`pastor` may only read docs whose `church` matches their own
 * and may only change `role` + `updatedAt`. Callers must therefore pass a
 * `churchFilter` for non-admin assigners (the unfiltered query would be denied)
 * and must restrict `role` to the self-assignable set client-side.
 */
interface RoleAssignmentService {
    suspend fun loadUsers(churchFilter: String?): List<ManagedUser>

    /**
     * @param writeIdField when true (admin only) also stamps `id == uid` so the
     * iOS admin list decoder — which decodes a non-optional `id` field — can
     * read Android-written docs. Church-scoped assigners must leave it false:
     * the rules limit their update to `{ role, updatedAt }`.
     */
    suspend fun updateRole(uid: String, role: UserRole, writeIdField: Boolean)
}

@Singleton
class FirestoreRoleAssignmentService @Inject constructor(
    private val db: FirebaseFirestore,
) : RoleAssignmentService {

    override suspend fun loadUsers(churchFilter: String?): List<ManagedUser> {
        val collection = db.collection(UsersCollection)
        val query = churchFilter
            ?.trim()
            ?.takeUnless { it.isBlank() }
            ?.let { collection.whereEqualTo(ChurchField, it) }
            ?: collection

        val snapshot = query.get().await()
        return snapshot.documents.mapNotNull { document ->
            @Suppress("UNCHECKED_CAST")
            val data = document.data as? Map<String, Any?> ?: return@mapNotNull null
            data.toManagedUser(document.id)
        }
    }

    override suspend fun updateRole(uid: String, role: UserRole, writeIdField: Boolean) {
        require(uid.isNotBlank()) { "User id is required." }

        val payload = linkedMapOf<String, Any?>(
            RoleField to role.rawValue,
            UpdatedAtField to FieldValue.serverTimestamp(),
        )
        if (writeIdField) payload[IdField] = uid

        db.collection(UsersCollection)
            .document(uid)
            .set(payload, SetOptions.merge())
            .await()
    }

    private companion object {
        const val UsersCollection = "users"
        const val RoleField = "role"
        const val UpdatedAtField = "updatedAt"
        const val ChurchField = "church"
        const val IdField = "id"
    }
}

class FakeRoleAssignmentService(
    initialUsers: List<ManagedUser> = previewManagedUsers(),
    var shouldFail: Boolean = false,
) : RoleAssignmentService {
    private val users = initialUsers.toMutableList()

    override suspend fun loadUsers(churchFilter: String?): List<ManagedUser> {
        failIfNeeded()
        val filter = churchFilter?.trim()?.takeUnless { it.isBlank() } ?: return users.toList()
        return users.filter { it.church.equals(filter, ignoreCase = true) }
    }

    override suspend fun updateRole(uid: String, role: UserRole, writeIdField: Boolean) {
        failIfNeeded()
        val index = users.indexOfFirst { it.id == uid }
        if (index >= 0) users[index] = users[index].copy(role = role, updatedAt = Date())
    }

    private fun failIfNeeded() {
        if (shouldFail) throw IllegalStateException("FakeRoleAssignmentService configured to fail.")
    }
}

fun previewManagedUsers(): List<ManagedUser> = listOf(
    ManagedUser(
        id = "u-lea",
        displayName = "Léa Müller",
        email = "lea@example.org",
        church = "Lausanne Adventist Church",
        role = UserRole.User,
        photoUrl = null,
        updatedAt = Date(),
    ),
    ManagedUser(
        id = "u-david",
        displayName = "David Chen",
        email = "david@example.org",
        church = "Paris 17e Adventiste",
        role = UserRole.Adult,
        photoUrl = null,
        updatedAt = Date(),
    ),
    ManagedUser(
        id = "u-marc",
        displayName = "Marc Dupont",
        email = "marc@example.org",
        church = "Lausanne Adventist Church",
        role = UserRole.YouthDirector,
        photoUrl = null,
        updatedAt = Date(),
    ),
)

@Module
@InstallIn(SingletonComponent::class)
abstract class RoleAssignmentBindings {
    @Binds
    @Singleton
    abstract fun bindRoleAssignmentService(impl: FirestoreRoleAssignmentService): RoleAssignmentService
}
