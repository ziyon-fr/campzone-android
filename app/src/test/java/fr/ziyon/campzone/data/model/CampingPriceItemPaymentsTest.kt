package fr.ziyon.campzone.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampingPriceItemPaymentsTest {

    private fun item(
        amountCents: Int = 6000,
        currency: String = "EUR",
        options: List<CampingPaymentOption> = listOf(CampingPaymentOption.CardOneTime),
        iban: String? = null,
    ) = CampingPriceItem(
        id = "item-1",
        name = "Lodging",
        details = "",
        amountCents = amountCents,
        currency = currency,
        paymentOptions = options,
        iban = iban,
    )

    @Test
    fun installmentAmountSplitsIntoThreeRoundedSlices() {
        assertEquals(3, CAMPING_PRICE_ITEM_INSTALLMENT_COUNT)
        assertEquals(2000, item(amountCents = 6000).installmentAmountCents)
        // 100 / 3 = 33.33 -> rounds to 33
        assertEquals(33, item(amountCents = 100).installmentAmountCents)
        // Never below 1 for a positive amount.
        assertEquals(1, item(amountCents = 1).installmentAmountCents)
        // Free items split to 0.
        assertEquals(0, item(amountCents = 0).installmentAmountCents)
    }

    @Test
    fun resolvedCurrencyNormalizesAndDefaults() {
        assertEquals("EUR", item(currency = "eur").resolvedCurrency)
        assertEquals("USD", item(currency = " usd ").resolvedCurrency)
        assertEquals("EUR", item(currency = "   ").resolvedCurrency)
    }

    @Test
    fun offeredPaymentMeansReflectOptionsAndIban() {
        val card = item(options = listOf(CampingPaymentOption.CardOneTime))
        assertTrue(card.offersCardOneTime)
        assertFalse(card.offersInstallments)
        assertFalse(card.offersBankTransfer)
        assertTrue(card.offersCardPayment)

        val installments = item(options = listOf(CampingPaymentOption.CardInstallments))
        assertTrue(installments.offersInstallments)
        assertTrue(installments.offersCardPayment)

        // Bank transfer needs a usable IBAN to be offered.
        val transferNoIban = item(options = listOf(CampingPaymentOption.BankTransfer))
        assertFalse(transferNoIban.offersBankTransfer)
        assertFalse(transferNoIban.offersCardPayment)

        val transferWithIban = item(
            options = listOf(CampingPaymentOption.BankTransfer),
            iban = "FR7630004000050000000000123",
        )
        assertTrue(transferWithIban.offersBankTransfer)
    }
}
