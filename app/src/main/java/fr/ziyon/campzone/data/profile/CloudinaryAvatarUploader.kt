package fr.ziyon.campzone.data.profile

import com.google.firebase.auth.FirebaseAuth
import fr.ziyon.campzone.BuildConfig
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CloudinaryUploadResult(
    val secureUrl: String,
    val publicId: String,
)

@Singleton
class CloudinaryAvatarUploader @Inject constructor(
    private val auth: FirebaseAuth,
) {
    suspend fun uploadAvatar(
        uid: String,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult {
        val token = auth.currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: error("There is no signed-in user.")

        return withContext(Dispatchers.IO) {
            val assetId = "$uid-${UUID.randomUUID()}"
            val signature = requestSignature(token = token, assetId = assetId)
            uploadToCloudinary(
                signature = signature,
                bytes = bytes,
                mimeType = mimeType,
                fileName = "$assetId.$fileExtension",
            )
        }
    }

    private fun requestSignature(
        token: String,
        assetId: String,
    ): CloudinarySignature {
        val params = JSONObject()
            .put("public_id", assetId)
            .put("folder", AvatarFolder)
            .put("tags", "campzone,avatar")
            .put("overwrite", true)
        val body = JSONObject()
            .put("paramsToSign", params)
            .put("resourceType", "image")

        val response = postJson(
            url = "${BuildConfig.BACKEND_BASE_URL}/cloudinary/sign",
            bearerToken = token,
            body = body,
        )
        val root = JSONObject(response)
        val data = if (root.optBoolean("success") && root.has("data")) {
            root.getJSONObject("data")
        } else {
            root
        }

        return CloudinarySignature(
            uploadUrl = data.getString("uploadURL"),
            apiKey = data.getString("apiKey"),
            signature = data.getString("signature"),
            timestamp = data.getString("timestamp"),
            signedParams = data.getJSONObject("signedParams").toStringMap(),
        )
    }

    private fun uploadToCloudinary(
        signature: CloudinarySignature,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): CloudinaryUploadResult {
        val boundary = "Campzone-${UUID.randomUUID()}"
        val connection = (URL(signature.uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        DataOutputStream(connection.outputStream).use { output ->
            signature.signedParams.forEach { (key, value) ->
                output.writeFormField(boundary, key, value)
            }
            output.writeFormField(boundary, "api_key", signature.apiKey)
            output.writeFormField(boundary, "signature", signature.signature)
            output.writeFormField(boundary, "timestamp", signature.timestamp)
            output.writeFileField(boundary, "file", fileName, mimeType, bytes)
            output.writeBytes("--$boundary--\r\n")
            output.flush()
        }

        val response = connection.readResponse()
        val json = JSONObject(response)
        return CloudinaryUploadResult(
            secureUrl = json.getString("secure_url"),
            publicId = json.getString("public_id"),
        )
    }

    private fun postJson(
        url: String,
        bearerToken: String,
        body: JSONObject,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        return connection.readResponse()
    }

    private fun HttpURLConnection.readResponse(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) {
            throw IllegalStateException(response.ifBlank { "Cloudinary upload failed." })
        }
        return response
    }

    private fun DataOutputStream.writeFormField(
        boundary: String,
        name: String,
        value: String,
    ) {
        writeBytes("--$boundary\r\n")
        writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        write(value.toByteArray(Charsets.UTF_8))
        writeBytes("\r\n")
    }

    private fun DataOutputStream.writeFileField(
        boundary: String,
        name: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        writeBytes("--$boundary\r\n")
        writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\n")
        writeBytes("Content-Type: $mimeType\r\n\r\n")
        write(bytes)
        writeBytes("\r\n")
    }

    private fun JSONObject.toStringMap(): Map<String, String> =
        keys().asSequence().associateWith { key -> get(key).toString() }

    private data class CloudinarySignature(
        val uploadUrl: String,
        val apiKey: String,
        val signature: String,
        val timestamp: String,
        val signedParams: Map<String, String>,
    )

    private companion object {
        const val AvatarFolder = "campzone/avatars"
    }
}
