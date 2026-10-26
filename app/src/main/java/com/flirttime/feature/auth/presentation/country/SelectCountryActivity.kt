package com.flirttime.feature.auth.presentation.country

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.databinding.ActivitySelectCountryBinding
import com.flirttime.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SelectCountryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectCountryBinding
    private val authRepository: AuthRepository by inject()

    private lateinit var adapter: SelectCountryAdapter
    private var allCountries: MutableList<CountryUiModel> = mutableListOf()
    private var filteredCountries: MutableList<CountryUiModel> = mutableListOf()
    private var selectedCountry: CountryUiModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySelectCountryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setupEdgeToEdge()
        setupRecycler()
        initCountries()
        setupSearch()
        setupListeners()
        loadCountriesFromApi()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            binding.main.updatePadding(
                top = statusBarInsets.top,
                bottom = navBarInsets.bottom
            )

            windowInsets
        }
    }

    private fun setupRecycler() {
        adapter = SelectCountryAdapter(filteredCountries) { item ->
            selectCountryItem(item)
        }
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter
    }

    private fun selectCountryItem(item: CountryUiModel) {
        selectedCountry = item
        allCountries.forEach { it.isSelected = (it.phoneCode == item.phoneCode && it.name == item.name) }
        filteredCountries.forEach { it.isSelected = (it.phoneCode == item.phoneCode && it.name == item.name) }
        adapter.notifyDataSetChanged()

        binding.tvContinue.isEnabled = true
        binding.tvContinue.alpha = 1.0f
    }

    private fun initCountries() {
        val currentCode = intent.getStringExtra("countryCode") ?: "+91"

        val defaultList = listOf(
            CountryUiModel("India", "+91", "IN", flagResId = R.drawable.img_loginnumber_flag),
            CountryUiModel("United States", "+1", "US"),
            CountryUiModel("United Kingdom", "+44", "GB"),
            CountryUiModel("Canada", "+1", "CA"),
            CountryUiModel("Australia", "+61", "AU"),
            CountryUiModel("United Arab Emirates", "+971", "AE"),
            CountryUiModel("Germany", "+49", "DE"),
            CountryUiModel("France", "+33", "FR"),
            CountryUiModel("Spain", "+34", "ES"),
            CountryUiModel("Italy", "+39", "IT"),
            CountryUiModel("Brazil", "+55", "BR"),
            CountryUiModel("Mexico", "+52", "MX"),
            CountryUiModel("Russia", "+7", "RU"),
            CountryUiModel("Japan", "+81", "JP"),
            CountryUiModel("South Korea", "+82", "KR"),
            CountryUiModel("Singapore", "+65", "SG"),
            CountryUiModel("Malaysia", "+60", "MY"),
            CountryUiModel("Indonesia", "+62", "ID"),
            CountryUiModel("Philippines", "+63", "PH"),
            CountryUiModel("South Africa", "+27", "ZA"),
            CountryUiModel("Saudi Arabia", "+966", "SA"),
            CountryUiModel("Turkey", "+90", "TR"),
            CountryUiModel("Netherlands", "+31", "NL"),
            CountryUiModel("New Zealand", "+64", "NZ"),
            CountryUiModel("Sweden", "+46", "SE"),
            CountryUiModel("Switzerland", "+41", "CH"),
            CountryUiModel("Poland", "+48", "PL"),
            CountryUiModel("Pakistan", "+92", "PK"),
            CountryUiModel("Bangladesh", "+880", "BD"),
            CountryUiModel("Nepal", "+977", "NP"),
            CountryUiModel("Sri Lanka", "+94", "LK")
        )

        allCountries.clear()
        defaultList.forEach { country ->
            val isSelected = country.phoneCode == currentCode
            if (isSelected) selectedCountry = country
            allCountries.add(country.copy(isSelected = isSelected))
        }

        filteredCountries.clear()
        filteredCountries.addAll(allCountries)
        adapter.updateList(filteredCountries)

        if (selectedCountry != null) {
            binding.tvContinue.isEnabled = true
            binding.tvContinue.alpha = 1.0f
        }
    }

    private fun loadCountriesFromApi() {
        lifecycleScope.launch {
            authRepository.getCountries().collect { result ->
                if (result is Resource.Success && result.data.isNotEmpty()) {
                    val apiCountries = result.data.map { apiItem ->
                        val code = if (apiItem.countryCode.startsWith("+")) {
                            apiItem.countryCode
                        } else {
                            "+${apiItem.countryCode}"
                        }
                        CountryUiModel(
                            name = apiItem.name,
                            phoneCode = code,
                            countryCode = apiItem.countryCode,
                            flagResId = apiItem.flags ?: (if (apiItem.countryCode.contains("91")) R.drawable.img_loginnumber_flag else null)
                        )
                    }

                    if (apiCountries.isNotEmpty()) {
                        val currentCode = selectedCountry?.phoneCode ?: intent.getStringExtra("countryCode") ?: "+91"
                        allCountries.clear()
                        apiCountries.forEach { country ->
                            val isSelected = country.phoneCode == currentCode
                            if (isSelected) selectedCountry = country
                            allCountries.add(country.copy(isSelected = isSelected))
                        }
                        filterData(binding.etSearch.text?.toString().orEmpty())
                    }
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterData(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterData(query: String) {
        val q = query.trim().lowercase()
        filteredCountries.clear()
        if (q.isEmpty()) {
            filteredCountries.addAll(allCountries)
        } else {
            val matches = allCountries.filter {
                it.name.lowercase().contains(q) || it.phoneCode.lowercase().contains(q)
            }
            filteredCountries.addAll(matches)
        }

        adapter.updateList(filteredCountries)

        if (filteredCountries.isEmpty()) {
            binding.rv.visibility = View.GONE
            binding.placeHolder.visibility = View.VISIBLE
        } else {
            binding.rv.visibility = View.VISIBLE
            binding.placeHolder.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        binding.tvContinue.setOnClickListener {
            selectedCountry?.let { country ->
                val resultIntent = Intent().apply {
                    putExtra("countryName", country.name)
                    putExtra("countryCode", country.phoneCode)
                    country.flagResId?.let { putExtra("countryFlagResId", it) }
                    country.flagUrl?.let { putExtra("countryFlagUrl", it) }
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}
