package fr.ziyon.campzone.testing

import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider

class FakeStringProvider : StringProvider {
    override fun get(id: Int, vararg args: Any): String {
        val value = when (id) {
            R.string.payment_completed_next -> "Payment completed. Continue with the next participant."
            R.string.payment_completed -> "Payment completed."
            R.string.payment_details_load_error -> "Payment details could not be loaded."
            R.string.payment_prepare_error -> "Payment could not be prepared."
            R.string.payment_confirm_error -> "Payment could not be confirmed."
            R.string.payment_canceled -> "Payment was canceled."
            R.string.payment_failed -> "Payment failed."
            R.string.payment_not_completed_status -> "Payment was not completed. Current status: %1\$s."
            R.string.payment_registration_line_item -> "Registration - %1\$s"
            R.string.payment_bus_fare_line_item -> "Bus fare - %1\$s"
            R.string.schedule_day_title -> "Day %1\$d"
            R.string.schedule_load_error -> "Failed to load schedule."
            R.string.schedule_reminder_saved -> "Reminder timing saved."
            R.string.schedule_reminder_save_error -> "Could not save reminder timing."
            R.string.schedule_program_saved -> "Program saved."
            R.string.schedule_program_deleted -> "Program deleted."
            R.string.schedule_program_save_error -> "Could not save program."
            R.string.schedule_program_delete_error -> "Could not delete program."
            R.string.schedule_program_saved_menu_sync_error -> "Program saved, but the food menu could not be synced."
            R.string.schedule_program_deleted_menu_sync_error -> "Program deleted, but the food menu could not be synced."
            R.string.badges_load_error -> "Badges could not be loaded."
            R.string.badges_award_load_error -> "Award badges could not be loaded."
            R.string.badges_self_award_error -> "Ask another leader to award badges that include you."
            R.string.badges_select_participant_error -> "Select at least one participant."
            R.string.badges_awarded -> "Achievement awarded."
            R.string.badges_awarded_many -> "Achievement awarded to %1\$d participants."
            R.string.badges_award_error -> "Achievement could not be awarded."
            R.string.badges_automatic_award_error -> "This badge is awarded automatically."
            else -> ""
        }
        return if (args.isEmpty()) value else value.format(*args)
    }
}
