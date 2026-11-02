package com.flirttime.feature.coins

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityCoinShopBinding
import com.flirttime.feature.subscription.PremiumActivity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class CoinsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoinShopBinding
    private val viewModel: CoinsViewModel by viewModel()
    private lateinit var adapter: CoinPackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoinShopBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = CoinPackAdapter { option -> viewModel.purchase(this, option) }
        binding.rvPacks.layoutManager = GridLayoutManager(this, 3)
        binding.rvPacks.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.load() }
        binding.cardPremiumUpsell.setOnClickListener { PremiumActivity.start(this) }
        binding.btnPrivacy.setOnClickListener {
            AppNavigator.navigateTo(
                this,
                Destination.Webview(
                    title = "Privacy policy",
                    url = "https://flirttime.in/page/privacy-policy"
                )
            )
        }

        observe()
        viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        binding.cardPremiumUpsell.visibility = if (viewModel.isPremium) View.GONE else View.VISIBLE
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.coinWallet.balance.collect { coins ->
                        binding.tvBalance.text = "$coins coins"
                    }
                }
                launch {
                    viewModel.state.collect { state ->
                        binding.progressPacks.visibility = if (state is CoinShopState.Loading) View.VISIBLE else View.GONE
                        binding.layoutError.visibility = if (state is CoinShopState.Failed) View.VISIBLE else View.GONE
                        binding.rvPacks.visibility = if (state is CoinShopState.Loaded) View.VISIBLE else View.GONE
                        if (state is CoinShopState.Failed) binding.tvError.text = state.message
                    }
                }
                launch {
                    viewModel.options.collect { adapter.submit(it, viewModel.purchasingPackId.value) }
                }
                launch {
                    viewModel.purchasingPackId.collect { adapter.submit(viewModel.options.value, it) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CoinPurchaseEvent.Purchased -> {
                                Toast.makeText(this@CoinsActivity, event.message, Toast.LENGTH_SHORT).show()
                                setResult(RESULT_OK)
                            }
                            CoinPurchaseEvent.Pending ->
                                Toast.makeText(this@CoinsActivity, "Purchase pending approval.", Toast.LENGTH_SHORT).show()
                            is CoinPurchaseEvent.Failed ->
                                Toast.makeText(this@CoinsActivity, event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
