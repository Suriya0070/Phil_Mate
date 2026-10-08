package com.jsf.app.medication_solution.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jsf.app.medication_solution.data.model.User
import com.jsf.app.medication_solution.data.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Login failed")
        getUserById(uid).getOrThrow()
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: UserRole,
        phone: String
    ): Result<User> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Registration failed")
        val user = User(
            id = uid,
            name = name,
            email = email,
            role = role.name,
            phone = phone,
            lastActiveAt = System.currentTimeMillis()
        )
        db.collection("users").document(uid).set(user).await()
        user
    }

    suspend fun getUserById(userId: String): Result<User> = runCatching {
        val doc = db.collection("users").document(userId).get().await()
        doc.toObject(User::class.java)?.copy(id = doc.id) ?: error("User not found")
    }

    suspend fun updateLastActive(userId: String) {
        runCatching {
            db.collection("users").document(userId)
                .update("lastActiveAt", System.currentTimeMillis()).await()
        }
    }

    suspend fun linkCaregiverToSenior(caregiverId: String, seniorEmail: String): Result<String> =
        runCatching {
            val query = db.collection("users")
                .whereEqualTo("email", seniorEmail)
                .whereEqualTo("role", UserRole.SENIOR.name)
                .get().await()
            val seniorDoc = query.documents.firstOrNull() ?: error("Senior not found with that email")
            val seniorId = seniorDoc.id
            db.collection("users").document(caregiverId)
                .update("linkedSeniorId", seniorId).await()
            db.collection("users").document(seniorId)
                .update(
                    "linkedCaregiverIds",
                    com.google.firebase.firestore.FieldValue.arrayUnion(caregiverId)
                ).await()
            seniorId
        }

    fun observeUser(userId: String): Flow<User> = callbackFlow {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snap, _ ->
                snap?.toObject(User::class.java)?.copy(id = snap.id)?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateMonitoring(seniorId: String, enabled: Boolean) {
        runCatching {
            db.collection("users").document(seniorId).update("isMonitored", enabled).await()
        }
    }

    suspend fun updateCheckInInterval(seniorId: String, intervalMinutes: Int) {
        runCatching {
            db.collection("users").document(seniorId)
                .update("checkInIntervalMinutes", intervalMinutes).await()
        }
    }

    fun logout() = auth.signOut()
}
