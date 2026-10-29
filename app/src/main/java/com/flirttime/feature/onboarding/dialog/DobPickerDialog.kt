package com.flirttime.feature.onboarding.dialog

import android.app.DatePickerDialog
import android.content.Context
import java.util.Calendar
import java.util.Locale

interface OnDateSelectedListener {
    fun onDateSelected(formattedDate: String, age: Int)
}

class DobPickerDialog(
    private val context: Context,
    private val listener: OnDateSelectedListener
) {
    fun show(currentDateString: String? = null) {
        val calendar = Calendar.getInstance()
        // Default to 18 years ago
        calendar.add(Calendar.YEAR, -18)

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedMonth = String.format(Locale.US, "%02d", selectedMonth + 1)
                val formattedDay = String.format(Locale.US, "%02d", selectedDay)
                val formattedDate = "$formattedDay/$formattedMonth/$selectedYear"

                val today = Calendar.getInstance()
                var age = today.get(Calendar.YEAR) - selectedYear
                if (today.get(Calendar.DAY_OF_YEAR) < calendar.get(Calendar.DAY_OF_YEAR)) {
                    age--
                }
                listener.onDateSelected(formattedDate, age)
            },
            year,
            month,
            day
        )

        // Set maximum date to 18 years ago today
        val maxCal = Calendar.getInstance()
        maxCal.add(Calendar.YEAR, -18)
        datePickerDialog.datePicker.maxDate = maxCal.timeInMillis

        // Set minimum date (e.g. 100 years ago)
        val minCal = Calendar.getInstance()
        minCal.add(Calendar.YEAR, -100)
        datePickerDialog.datePicker.minDate = minCal.timeInMillis

        datePickerDialog.show()
    }
}
