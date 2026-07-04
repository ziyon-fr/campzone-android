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
            R.string.schedule_day_title_saved -> "Day name saved."
            R.string.schedule_day_title_save_error -> "Could not save day name."
            R.string.schedule_validation_custom_type_required -> "Personalize the custom program type."
            R.string.schedule_program_saved_menu_sync_error -> "Program saved, but the food menu could not be synced."
            R.string.schedule_program_deleted_menu_sync_error -> "Program deleted, but the food menu could not be synced."
            R.string.schedule_reminder_dispatch_sync_error -> "Schedule saved, but reminder dispatch could not be synced."
            R.string.schedule_program_deleted_reminder_sync_error -> "Program deleted, but reminder dispatch could not be synced."
            R.string.food_menu_load_error -> "Failed to load food menu."
            R.string.food_menu_saved -> "Menu saved."
            R.string.food_menu_save_error -> "Could not save menu entry."
            R.string.food_menu_deleted -> "Menu entry deleted."
            R.string.food_menu_delete_error -> "Could not delete menu entry."
            R.string.food_menu_validation_dish_required -> "Add at least one dish."
            R.string.badges_load_error -> "Badges could not be loaded."
            R.string.badges_award_load_error -> "Award badges could not be loaded."
            R.string.badges_self_award_error -> "Ask another leader to award badges that include you."
            R.string.badges_select_participant_error -> "Select at least one participant."
            R.string.badges_awarded -> "Achievement awarded."
            R.string.badges_awarded_many -> "Achievement awarded to %1\$d participants."
            R.string.badges_award_error -> "Achievement could not be awarded."
            R.string.badges_automatic_award_error -> "This badge is awarded automatically."
            R.string.common_camping -> "Camping"
            R.string.songbook_title_required -> "Song title is required."
            R.string.songbook_lyrics_required -> "Lyrics are required."
            R.string.songbook_lyrics_text_required -> "Lyrics text is required."
            R.string.songbook_audio_unsupported_error -> "Choose a supported audio file: MP3, M4A, AAC, or WAV."
            R.string.songbook_operation_failed -> "Songbook operation failed. Please try again."
            R.string.songbook_saved -> "Song saved."
            R.string.songbook_deleted -> "Song deleted."
            R.string.songbook_order_updated -> "Song order updated."
            R.string.songbook_theme_pinned -> "Theme song pinned."
            R.string.songbook_sign_in_favorite -> "Sign in to favorite songs."
            R.string.songbook_audio_play_error -> "This song could not be played."
            R.string.songbook_audio_missing -> "This song does not have playable audio yet."
            R.string.songbook_duplicate_track_type -> "Each voice kit can only be used once, except Other."
            R.string.songbook_main_audio_required -> "Choose one attached track as the Main Song."
            R.string.songbook_remote_url_invalid -> "Enter a valid HTTP or HTTPS audio URL."
            R.string.songbook_remote_url_duplicate -> "This audio file is already attached."
            R.string.songbook_remote_url_unsupported -> "Remote audio must point to an MP3, M4A, AAC, or WAV file."
            R.string.songbook_restricted_message -> "Only admins can manage the songbook."
            R.string.songbook_catalog_load_failed -> "The song catalog could not be loaded."
            R.string.songbook_catalog_import_failed -> "The selected catalog songs could not be imported."
            R.string.program_not_found_message -> "The selected schedule program could not be loaded."
            R.string.program_attendance_load_failed -> "Program attendance could not be loaded."
            R.string.program_attendance_recorded -> "Attendance recorded."
            R.string.program_attendance_corrected -> "Attendance corrected."
            R.string.program_attendance_removed -> "Attendance removed."
            R.string.program_attendance_save_failed -> "Attendance could not be saved."
            R.string.program_attendance_remove_failed -> "Attendance could not be removed."
            R.string.camping_template_load_failed -> "Template camp could not be loaded."
            R.string.camping_template_create_failed -> "Template camp could not be created."
            R.string.packing_title -> "Packing Checklist"
            R.string.packing_share_title_camp -> "Packing checklist · %1\$s"
            R.string.packing_personal_notes -> "Personal notes"
            R.string.packing_my_items -> "My items"
            R.string.packing_category_spiritual -> "Spiritual & Essentials"
            R.string.packing_item_bible -> "Bible"
            R.string.packing_item_energy -> "Lots of energy"
            R.string.packing_item_alarm -> "Alarm clock"
            R.string.packing_category_shelter -> "Shelter & Sleeping"
            R.string.packing_item_tent -> "Tent"
            R.string.packing_item_sleeping_pad -> "Sleeping pad"
            R.string.packing_item_blanket -> "Blanket"
            R.string.packing_item_flashlight -> "Flashlight"
            R.string.packing_item_chair -> "Chair"
            R.string.packing_category_food -> "Food & Kitchen"
            R.string.packing_item_plate -> "Plate"
            R.string.packing_item_cup -> "Cup"
            R.string.packing_item_mug -> "Mug"
            R.string.packing_item_bowl -> "Bowl"
            R.string.packing_item_cutlery -> "Cutlery"
            R.string.packing_item_thermos -> "Thermos"
            R.string.packing_item_snacks -> "Snacks to nibble between meals"
            R.string.packing_category_clothing -> "Clothing & Footwear"
            R.string.packing_item_sneakers -> "Sneakers"
            R.string.packing_item_flip_flops -> "Flip-flops"
            R.string.packing_item_water_shoes -> "Water shoes"
            R.string.packing_item_swimsuit -> "One-piece swimsuit"
            R.string.packing_item_shorts -> "Shorts"
            R.string.packing_item_activity_clothes -> "Activity clothes"
            R.string.packing_item_hat -> "Cap / Hat"
            R.string.packing_category_hygiene -> "Hygiene & Personal care"
            R.string.packing_item_towel -> "Towel"
            R.string.packing_item_toilet_paper -> "Toilet paper"
            R.string.packing_item_sunscreen -> "Sunscreen"
            R.string.packing_item_sunglasses -> "Sunglasses"
            R.string.packing_item_medicine -> "Medication"
            else -> ""
        }
        return if (args.isEmpty()) value else value.format(*args)
    }

    override fun getQuantity(id: Int, quantity: Int, vararg args: Any): String {
        val value = when (id) {
            R.plurals.songbook_catalog_added_count -> {
                if (quantity == 1) {
                    "Added %1\$d song to the songbook."
                } else {
                    "Added %1\$d songs to the songbook."
                }
            }
            R.plurals.packing_items_ready -> {
                if (quantity == 1) {
                    "%1\$d of %2\$d item ready"
                } else {
                    "%1\$d of %2\$d items ready"
                }
            }
            else -> "%1\$d"
        }
        return if (args.isEmpty()) value else value.format(*args)
    }
}
