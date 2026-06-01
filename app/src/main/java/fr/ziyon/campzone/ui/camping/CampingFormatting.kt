package fr.ziyon.campzone.ui.camping

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzBadgeTone
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Single calendar date, e.g. "31 May 2026" (registration deadline display). */
internal fun campingDate(date: Date): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)

/** Human date range for a camping, e.g. "18–24 Jul 2026" or "9 Oct 2026". */
internal fun campingDateRange(start: Date, end: Date): String {
    val locale = Locale.getDefault()
    val full = SimpleDateFormat("d MMM yyyy", locale)
    if (isSameDay(start, end)) return full.format(start)

    val startSameYear = yearOf(start) == yearOf(end)
    val startFormat = SimpleDateFormat(if (startSameYear) "d MMM" else "d MMM yyyy", locale)
    return "${startFormat.format(start)} – ${full.format(end)}"
}

internal fun CampingRegistrationStatus.badgeTone(): CzBadgeTone = when (this) {
    CampingRegistrationStatus.Open -> CzBadgeTone.Success
    CampingRegistrationStatus.Closed -> CzBadgeTone.Warning
    CampingRegistrationStatus.Cancelled -> CzBadgeTone.Error
}

@Composable
internal fun CampingRegistrationStatus.label(): String = stringResource(
    when (this) {
        CampingRegistrationStatus.Open -> R.string.camping_status_open
        CampingRegistrationStatus.Closed -> R.string.camping_status_closed
        CampingRegistrationStatus.Cancelled -> R.string.camping_status_cancelled
    },
)

internal fun RegistrationApprovalStatus.badgeTone(): CzBadgeTone = when (this) {
    RegistrationApprovalStatus.Approved -> CzBadgeTone.Success
    RegistrationApprovalStatus.Pending -> CzBadgeTone.Warning
    RegistrationApprovalStatus.Waitlisted -> CzBadgeTone.Neutral
    RegistrationApprovalStatus.Rejected -> CzBadgeTone.Error
}

@Composable
internal fun RegistrationApprovalStatus.label(): String = stringResource(
    when (this) {
        RegistrationApprovalStatus.Pending -> R.string.registration_status_pending
        RegistrationApprovalStatus.Approved -> R.string.registration_status_approved
        RegistrationApprovalStatus.Rejected -> R.string.registration_status_rejected
        RegistrationApprovalStatus.Waitlisted -> R.string.registration_status_waitlisted
    },
)

private fun isSameDay(a: Date, b: Date): Boolean {
    val calA = Calendar.getInstance().apply { time = a }
    val calB = Calendar.getInstance().apply { time = b }
    return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
        calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
}

private fun yearOf(date: Date): Int =
    Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
