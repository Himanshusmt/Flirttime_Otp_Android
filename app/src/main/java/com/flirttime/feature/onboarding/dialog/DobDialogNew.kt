package com.flirttime.feature.onboarding.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.GridLayoutManager
import com.calendarview.CompactCalendarView
import com.flirttime.databinding.PopupDobBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DobDialogNew(
    private val context: Context,
    private val listener: OnDateSelectedListener
) {
    var myDate: String = ""
    var myMonth: String = ""
    var myYear: String = ""

    private var dialog: Dialog? = null
    val popupDobBinding: PopupDobBinding = PopupDobBinding.inflate(LayoutInflater.from(context))
    private val yearsList: MutableList<String> = mutableListOf()
    private val gridLayoutManager: GridLayoutManager = GridLayoutManager(context, 5)

    private val monthNames: Array<String> = arrayOf(
        "Jan", "Feb", "Mar", "Apr",
        "May", "Jun", "Jul", "Aug",
        "Sep", "Oct", "Nov", "Dec"
    )

    init {
        dialog = Dialog(context).apply {
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setContentView(popupDobBinding.root)
            val width = (context.resources.displayMetrics.widthPixels * 0.85f).toInt()
            window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        myYear = (currentYear - 18).toString()

        popupDobBinding.calendarView.setUseThreeLetterAbbreviation(true)
        popupDobBinding.calendarView.setListener(object : CompactCalendarView.CompactCalendarViewListener {
            override fun onDayClick(dateClicked: Date) {
                isDateValid(dateClicked)
            }

            override fun onMonthScroll(firstDayOfNewMonth: Date) {
                isDateValid(firstDayOfNewMonth)
                val cal = Calendar.getInstance().apply { time = firstDayOfNewMonth }
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                popupDobBinding.monthTv.text = monthNames[month]
                popupDobBinding.yearTv.text = "$year"
            }
        })

        // Year picker grid toggle
        popupDobBinding.yearTv.setOnClickListener {
            if (popupDobBinding.yearsRecyclerView.visibility == View.VISIBLE) {
                showCalendar()
            } else {
                showYearPicker()
            }
        }

        // Stepper: 1 month forward/backward
        popupDobBinding.rightBtn.setOnClickListener {
            popupDobBinding.calendarView.scrollRight()
            showCalendar()
        }
        popupDobBinding.leftBtn.setOnClickListener {
            popupDobBinding.calendarView.scrollLeft()
            showCalendar()
        }

        // Stepper: 1 year forward/backward (12 months scroll)
        popupDobBinding.yearRightBtn.setOnClickListener {
            for (i in 1..12) {
                popupDobBinding.calendarView.scrollRight()
            }
            showCalendar()
        }
        popupDobBinding.yearLeftBtn.setOnClickListener {
            for (i in 1..12) {
                popupDobBinding.calendarView.scrollLeft()
            }
            showCalendar()
        }

        // Save CTA button
        popupDobBinding.tvContinue.setOnClickListener {
            if (popupDobBinding.tvContinue.isEnabled && myDate.isNotEmpty()) {
                val formattedDate = "$myDate/$myMonth/$myYear"
                val age = calculateAge()
                listener.onDateSelected(formattedDate, age)
                dismiss()
            }
        }
    }

    private fun showYearPicker() {
        popupDobBinding.yearsRecyclerView.visibility = View.VISIBLE
        popupDobBinding.calendarView.visibility = View.GONE

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        yearsList.clear()
        for (y in 1900..currentYear) {
            yearsList.add(y.toString())
        }

        val adapter = DobDialogYearAdapter(yearsList) { selectedYearStr, _ ->
            onYearSelected(selectedYearStr)
        }
        popupDobBinding.yearsRecyclerView.layoutManager = gridLayoutManager
        popupDobBinding.yearsRecyclerView.adapter = adapter

        // Scroll to selected year
        val index = yearsList.indexOf(myYear)
        if (index >= 0) {
            gridLayoutManager.scrollToPosition(index)
        }
    }

    private fun onYearSelected(newlySelectedYear: String) {
        var lastSelectedYear = myYear.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val targetYear = newlySelectedYear.toIntOrNull() ?: lastSelectedYear

        while (targetYear != lastSelectedYear) {
            if (targetYear < lastSelectedYear) {
                lastSelectedYear--
                for (i in 1..12) {
                    popupDobBinding.calendarView.scrollLeft()
                }
            } else {
                lastSelectedYear++
                for (i in 1..12) {
                    popupDobBinding.calendarView.scrollRight()
                }
            }
        }
        myYear = newlySelectedYear
        popupDobBinding.yearTv.text = myYear
        showCalendar()
    }

    private fun showCalendar() {
        if (popupDobBinding.yearsRecyclerView.visibility == View.VISIBLE) {
            popupDobBinding.yearsRecyclerView.visibility = View.GONE
            popupDobBinding.calendarView.visibility = View.VISIBLE
        }
    }

    private fun isDateValid(dateClicked: Date) {
        myDate = DateFormat.format("dd", dateClicked).toString()
        myMonth = DateFormat.format("MM", dateClicked).toString()
        myYear = DateFormat.format("yyyy", dateClicked).toString()

        val cal = Calendar.getInstance().apply { time = dateClicked }
        val monthIdx = cal.get(Calendar.MONTH)
        popupDobBinding.monthTv.text = monthNames[monthIdx]
        popupDobBinding.yearTv.text = myYear

        val age = calculateAge()
        val is18Plus = age >= 18

        popupDobBinding.tvContinue.isEnabled = is18Plus
        popupDobBinding.tvContinue.alpha = if (is18Plus) 1f else 0.5f
        popupDobBinding.tvAgeRequire.visibility = if (is18Plus) View.INVISIBLE else View.VISIBLE
    }

    private fun calculateAge(): Int {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val birthDate = sdf.parse("$myDate/$myMonth/$myYear") ?: return 0
            val birthCal = Calendar.getInstance().apply { time = birthDate }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            age
        } catch (e: Exception) {
            0
        }
    }

    fun show(currentDateStr: String? = null) {
        val calendar = Calendar.getInstance()
        if (!currentDateStr.isNullOrEmpty()) {
            try {
                val sdf = if (currentDateStr.contains("-")) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                } else {
                    SimpleDateFormat("dd/MM/yyyy", Locale.US)
                }
                val parsed = sdf.parse(currentDateStr)
                if (parsed != null) {
                    calendar.time = parsed
                }
            } catch (_: Exception) {
                calendar.add(Calendar.YEAR, -18)
            }
        } else {
            calendar.add(Calendar.YEAR, -18)
        }

        val date = calendar.time
        popupDobBinding.calendarView.setCurrentDate(date)

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        popupDobBinding.monthTv.text = monthNames[month]
        popupDobBinding.yearTv.text = "$year"

        showCalendar()
        isDateValid(date)

        dialog?.show()
    }

    fun dismiss() {
        if (dialog?.isShowing == true) {
            dialog?.dismiss()
        }
    }
}
