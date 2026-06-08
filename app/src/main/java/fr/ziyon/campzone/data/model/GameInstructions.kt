package fr.ziyon.campzone.data.model

import java.util.UUID

enum class GameInstructionAttachmentKind(val wireValue: String) {
    Image("image"),
    Pdf("pdf");

    companion object {
        fun fromWire(value: String?): GameInstructionAttachmentKind =
            entries.firstOrNull { it.wireValue == value } ?: Image
    }
}

data class GameInstructionAttachment(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val publicId: String,
    val kind: GameInstructionAttachmentKind = GameInstructionAttachmentKind.Image,
    val fileName: String? = null,
) {
    val displayName: String
        get() = fileName?.trim()?.takeUnless { it.isBlank() }
            ?: if (kind == GameInstructionAttachmentKind.Pdf) "Document.pdf" else "Image"
}

data class GameInstructions(
    val title: String = "",
    val description: String = "",
    val images: List<GameInstructionAttachment> = emptyList(),
) {
    val isEmpty: Boolean
        get() = title.trim().isEmpty() && description.trim().isEmpty() && images.isEmpty()
}

internal fun Map<String, Any?>.toGameInstructions(): GameInstructions =
    GameInstructions(
        title = rawStringValue("title").orEmpty(),
        description = rawStringValue("description").orEmpty(),
        images = mapListValue("images").mapNotNull { it.toGameInstructionAttachmentOrNull() },
    )

internal fun Map<String, Any?>.toGameInstructionAttachmentOrNull(): GameInstructionAttachment? {
    val url = stringValue("url") ?: return null
    val publicId = stringValue("publicID") ?: return null
    return GameInstructionAttachment(
        id = stringValue("id") ?: UUID.randomUUID().toString(),
        url = url,
        publicId = publicId,
        kind = GameInstructionAttachmentKind.fromWire(stringValue("kind")),
        fileName = stringValue("fileName"),
    )
}

internal object GameInstructionsPayload {
    fun instructionsPayload(instructions: GameInstructions): Map<String, Any?> =
        linkedMapOf(
            "title" to instructions.title.trim(),
            "description" to instructions.description.trim(),
            "images" to instructions.images.map(::attachmentMap),
        )

    fun attachmentMap(attachment: GameInstructionAttachment): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to attachment.id,
            "url" to attachment.url,
            "publicID" to attachment.publicId,
        )
        if (attachment.kind != GameInstructionAttachmentKind.Image) map["kind"] = attachment.kind.wireValue
        attachment.fileName?.trim()?.takeUnless { it.isBlank() }?.let { map["fileName"] = it }
        return map
    }
}
