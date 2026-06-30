package fr.ziyon.campzone.ui.camping

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ziyon.campzone.R
import fr.ziyon.campzone.data.model.CampingPaymentOption
import fr.ziyon.campzone.data.model.TransportationMode

@Composable
internal fun TransportationMode.localizedDisplayName(): String = stringResource(
    when (this) {
        TransportationMode.Bus -> R.string.transport_mode_bus
        TransportationMode.Coach -> R.string.transport_mode_coach
        TransportationMode.Minibus -> R.string.transport_mode_minibus
        TransportationMode.Shuttle -> R.string.transport_mode_shuttle
        TransportationMode.Train -> R.string.transport_mode_train
        TransportationMode.Carpool -> R.string.transport_mode_carpool
        TransportationMode.OwnCar -> R.string.transport_mode_own_car
        TransportationMode.Plane -> R.string.transport_mode_flight
        TransportationMode.Boat -> R.string.transport_mode_boat_ferry
        TransportationMode.Bike -> R.string.transport_mode_bicycle
        TransportationMode.OnFoot -> R.string.transport_mode_on_foot
        TransportationMode.Other -> R.string.transport_mode_other
    }
)

@Composable
internal fun CampingPaymentOption.localizedDisplayName(): String = stringResource(
    when (this) {
        CampingPaymentOption.CardOneTime -> R.string.payment_option_card_one_time
        CampingPaymentOption.CardInstallments -> R.string.payment_option_card_installments
        CampingPaymentOption.BankTransfer -> R.string.payment_option_bank_transfer
    }
)
