package fr.ziyon.campzone.data.profile

data class CampingPreview(
    val id: String,
    val title: String,
    val dateRange: String,
    val location: String,
    val registrationStatus: String,
    val registrationCapacity: Float,
    val registeredMembersAmount: Float,

    // Cloudinary secure_url
    val logoURL: String? = null

)