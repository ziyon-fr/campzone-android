package fr.ziyon.campzone.ui.transportation

import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationCheckpoint
import fr.ziyon.campzone.data.model.TransportationLeg
import fr.ziyon.campzone.data.model.TransportationLegProgress
import fr.ziyon.campzone.data.model.TransportationScanEvent

/**
 * Pure read-side groupings of a booking list, mirroring the computed lists on
 * the iOS `TransportationObserver`. Kept as free functions so both the screens
 * (computing from the collected `bookings` flow) and the unit tests can reuse
 * them without duplicating VM state.
 */

/** Active bookings still awaiting payment. */
fun List<TransportationBooking>.pendingPayment(): List<TransportationBooking> =
    filter { it.isActive && it.paymentStatus == fr.ziyon.campzone.data.model.TransportationPaymentStatus.Unpaid }

/** Active, fare-settled bookings whose [leg] has not yet seen a departure scan. */
fun List<TransportationBooking>.notStarted(leg: TransportationLeg): List<TransportationBooking> =
    filter {
        it.isActive &&
            it.paymentStatus.allowsBoarding &&
            it.progress(leg) == TransportationLegProgress.NotStarted &&
            (leg == TransportationLeg.Outbound || it.coversReturn)
    }

/** Active bookings whose [leg] has departed but not yet arrived. */
fun List<TransportationBooking>.inTransit(leg: TransportationLeg): List<TransportationBooking> =
    filter {
        it.isActive &&
            it.progress(leg) == TransportationLegProgress.InTransit &&
            (leg == TransportationLeg.Outbound || it.coversReturn)
    }

/** Active bookings whose [leg] has fully arrived. */
fun List<TransportationBooking>.arrived(leg: TransportationLeg): List<TransportationBooking> =
    filter {
        it.isActive &&
            it.progress(leg) == TransportationLegProgress.Arrived &&
            (leg == TransportationLeg.Outbound || it.coversReturn)
    }

/** Active bookings whose whole trip (outbound + return when applicable) is done. */
fun List<TransportationBooking>.completedTrips(): List<TransportationBooking> =
    filter { it.isActive && it.isTripComplete }

/** Cancelled / inactive bookings. */
fun List<TransportationBooking>.inactive(): List<TransportationBooking> =
    filter { !it.isActive }

/** How many active fare-settled bookings the [leg] applies to (denominator for tallies). */
fun List<TransportationBooking>.legTotal(leg: TransportationLeg): Int =
    count {
        it.isActive &&
            it.paymentStatus.allowsBoarding &&
            (leg == TransportationLeg.Outbound || it.coversReturn)
    }

/** Full audit feed across every active booking, newest scan first. */
fun List<TransportationBooking>.allScanEvents(): List<Pair<TransportationBooking, TransportationScanEvent>> =
    filter { it.isActive }
        .flatMap { booking -> booking.scanHistory.map { booking to it } }
        .sortedByDescending { it.second.at }

/** Bookings at the camp on the outbound leg but not yet on a return leg. */
fun List<TransportationBooking>.atCamp(): List<TransportationBooking> =
    arrived(TransportationLeg.Outbound).filter {
        !it.coversReturn || it.progress(TransportationLeg.Return) == TransportationLegProgress.NotStarted
    }

/** First scan recorded for `(leg, checkpoint)`, or null. */
fun TransportationBooking.scanAt(
    leg: TransportationLeg,
    checkpoint: TransportationCheckpoint,
): TransportationScanEvent? = scanEvent(leg, checkpoint)
