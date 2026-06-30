package fr.ziyon.campzone.data.support

import java.net.URI
import java.util.Date
import java.util.UUID

enum class SupportLinkKind(val wireValue: String) {
    ChurchGiving("churchGiving"), CampScholarship("campScholarship"), AppDevelopment("appDevelopment"), SponsorInquiry("sponsorInquiry"), Other("other");
    companion object { fun fromWire(value: String?) = entries.firstOrNull { it.wireValue == value } ?: Other }
}

enum class SponsorTier(val wireValue: String) {
    MinistryPartner("ministryPartner"), Gold("gold"), Silver("silver"), Bronze("bronze"), Community("community");
    companion object { fun fromWire(value: String?) = entries.firstOrNull { it.wireValue == value } ?: Community }
}

data class SupportExternalLink(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val subtitle: String = "",
    val urlString: String = "",
    val kind: SupportLinkKind = SupportLinkKind.Other,
    val isPrimary: Boolean = false,
) {
    val normalizedUrl: String? get() = SupportUrlValidator.url(urlString)
    val isUsable: Boolean get() = title.trim().isNotEmpty() && normalizedUrl != null
}

data class SponsorAcknowledgement(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val note: String = "",
    val urlString: String = "",
    val tier: SponsorTier = SponsorTier.Community,
) { val normalizedUrl: String? get() = SupportUrlValidator.url(urlString) }

data class SupportHub(
    val intro: String,
    val impactNote: String,
    val links: List<SupportExternalLink> = emptyList(),
    val sponsors: List<SponsorAcknowledgement> = emptyList(),
    val updatedAt: Date? = null,
) {
    val externalLinks: List<SupportExternalLink> get() = links.filter { it.isUsable }.sortedWith(compareByDescending<SupportExternalLink> { it.isPrimary }.thenBy { it.title.lowercase() })
    val visibleSponsors: List<SponsorAcknowledgement> get() = sponsors.filter { it.name.trim().isNotEmpty() }.sortedWith(compareBy<SponsorAcknowledgement> { it.tier.ordinal }.thenBy { it.name.lowercase() })
    companion object {
        fun campFallback(title: String) = SupportHub(
            "$title may be supported by churches, families, and community sponsors.",
            "Published support pages open outside Campzone. Campzone does not collect donations, process payments, issue receipts, or unlock app features for supporters.",
        )
        val appFallback = SupportHub(
            "Campzone is built for church camps and maintained as a community tool.",
            "Development sponsorship links open outside Campzone. There are no in-app donations, tips, purchases, rewards, or unlocked digital features here.",
        )
    }
}

object SupportUrlValidator {
    private val allowed = setOf("https", "http", "sms", "mailto")
    fun normalize(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        return if (runCatching { URI(value).scheme }.getOrNull() != null) value else "https://$value"
    }
    fun url(raw: String): String? {
        val value = normalize(raw)
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in allowed) return null
        if (scheme in setOf("https", "http") && uri.host.isNullOrBlank()) return null
        if (scheme in setOf("sms", "mailto") && uri.schemeSpecificPart.isNullOrBlank()) return null
        return value
    }
}
