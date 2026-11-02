package com.flirttime.feature.subscription

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.flirttime.R
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityPremiumBinding
import com.flirttime.databinding.DialogCommonConfirmationBinding
import com.flirttime.databinding.ItemPremiumBenefitBinding
import com.flirttime.databinding.ItemPremiumFeatureRowBinding
import com.flirttime.databinding.ItemPremiumPlanTileBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.random.Random

/** iOS `SubscriptionPopUpType`: which locked feature sent the user to Premium. */
enum class SubscriptionPopUpType { CHAT, COMPLIMENT, SUPER_LIKE, UNLIMITED_SWIPES }

private data class PremiumBenefit(val iconRes: Int, val title: String, val detail: String)

/**
 * Android port of iOS `PremiumViewController` – the Flirttime Premium paywall.
 * Plans come from `GET api/v1/subscriptions`, purchases go through Google Play Billing and are verified
 * with `POST api/v1/subscriptions/purchase/verify`.
 */
class PremiumActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPremiumBinding
    private val viewModel: PremiumViewModel by viewModel()

    private val source: SubscriptionPopUpType? by lazy {
        intent.getStringExtra(EXTRA_SOURCE)?.let { name ->
            runCatching { SubscriptionPopUpType.valueOf(name) }.getOrNull()
        }
    }

    private val planAdapter = PremiumPlanAdapter { index -> viewModel.select(index) }
    private val carouselHandler = Handler(Looper.getMainLooper())
    private val dots = mutableListOf<View>()
    private var currentPage = 0
    private var carouselStarted = false
    private var lastOptionsSize = -1

    private val benefits = listOf(
        PremiumBenefit(R.drawable.ic_sf_eye_fill, "See who likes you", "Skip the guessing and match with your admirers instantly."),
        PremiumBenefit(R.drawable.ic_sf_infinity, "Unlimited Likes", "No daily limits. Discover as many people as you like."),
        PremiumBenefit(R.drawable.ic_sf_bubble_left_and_bubble_right_fill, "Chat before matching", "Start the conversation with anyone you click with."),
        PremiumBenefit(R.drawable.ic_sf_star_fill, "Weekly Super Likes", "Stand out and let them know they're special."),
        PremiumBenefit(R.drawable.ic_sf_gift_fill, "Unlimited compliments", "Make a great first impression every time."),
        PremiumBenefit(R.drawable.ic_sf_bolt_heart_fill, "Priority in Discover", "Get shown to more people, more often.")
    )

    private val autoScroll = object : Runnable {
        override fun run() {
            binding.pagerBenefits.setCurrentItem((currentPage + 1) % benefits.size, true)
            carouselHandler.postDelayed(this, CAROUSEL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupHero()
        setupCarousel()
        setupPlans()
        setupActions()
        observeViewModel()
        viewModel.load()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isPurchasing.value) return
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
        startCarousel()
    }

    override fun onPause() {
        super.onPause()
        carouselHandler.removeCallbacks(autoScroll)
        carouselStarted = false
    }

    // region Setup

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootPremium) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.layoutTopBar.updatePadding(top = bars.top + dp(6))
            binding.layoutFooter.updatePadding(bottom = nav.bottom + dp(2))
            insets
        }
    }

    private fun setupHero() {
        binding.tvHeroTitle.text = headlineText()
        binding.tvHeroSubtitle.text = subtitleText()
        binding.heroCard.doOnLayout { addFloatingHearts() }
    }

    private fun headlineText(): String = when (source) {
        SubscriptionPopUpType.CHAT -> "Start the conversation first"
        SubscriptionPopUpType.COMPLIMENT -> "Make an unforgettable first impression"
        SubscriptionPopUpType.SUPER_LIKE -> "Stand out with Super Likes"
        SubscriptionPopUpType.UNLIMITED_SWIPES -> "Keep the likes coming"
        null -> "Find your match faster"
    }

    private fun subtitleText(): String = when (source) {
        SubscriptionPopUpType.CHAT -> "Message anyone you click with, no match needed."
        SubscriptionPopUpType.COMPLIMENT -> "Send compliments that get you noticed."
        SubscriptionPopUpType.SUPER_LIKE -> "Let them know they're special before anyone else."
        SubscriptionPopUpType.UNLIMITED_SWIPES -> "No daily limits. Discover as many people as you like."
        null -> "More matches, more conversations, more you."
    }

    private val initialBenefitPage: Int
        get() = when (source) {
            SubscriptionPopUpType.CHAT -> 2
            SubscriptionPopUpType.SUPER_LIKE -> 3
            SubscriptionPopUpType.COMPLIMENT -> 4
            SubscriptionPopUpType.UNLIMITED_SWIPES -> 1
            null -> 0
        }

    /** iOS `addFloatingHearts`: translucent hearts bobbing on the gradient. */
    private fun addFloatingHearts() {
        val container = binding.heroHearts
        if (container.childCount > 0) return
        val w = binding.heroCard.width.toFloat()
        val h = binding.heroCard.height.toFloat()
        val reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

        // (x, y, size, alpha, angle) exactly as in iOS.
        data class Heart(val x: Float, val y: Float, val size: Float, val alpha: Float, val angle: Float)
        listOf(
            Heart(0.08f, 0.14f, 26f, 0.18f, -0.3f), Heart(0.86f, 0.10f, 34f, 0.16f, 0.25f),
            Heart(0.92f, 0.62f, 22f, 0.20f, 0.4f), Heart(0.05f, 0.70f, 30f, 0.14f, -0.2f),
            Heart(0.24f, 0.40f, 14f, 0.22f, 0.1f), Heart(0.76f, 0.36f, 16f, 0.20f, -0.15f)
        ).forEach { heart ->
            val width = dp(heart.size * 1.1f)
            val height = (width * 133f / 144f).toInt()
            val view = ImageView(this).apply {
                setImageResource(R.drawable.ic_sf_heart_fill)
                imageTintList = ColorStateList.valueOf(Color.argb((heart.alpha * 255).toInt(), 255, 255, 255))
                scaleType = ImageView.ScaleType.FIT_CENTER
                rotation = Math.toDegrees(heart.angle.toDouble()).toFloat()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            container.addView(view, FrameLayout.LayoutParams(width, height).apply {
                leftMargin = (heart.x * w - width / 2f).toInt()
                topMargin = (heart.y * h - height / 2f).toInt()
            })
            if (!reduceMotion) {
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -dp(8).toFloat()).apply {
                    duration = Random.nextLong(2200, 3400)
                    startDelay = Random.nextLong(0, 1000)
                    repeatMode = ObjectAnimator.REVERSE
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
    }

    private fun setupCarousel() {
        val pager = binding.pagerBenefits
        pager.adapter = BenefitAdapter(benefits)
        pager.offscreenPageLimit = 1
        pager.setCurrentItem(initialBenefitPage, false)
        currentPage = initialBenefitPage

        benefits.indices.forEach { _ ->
            val dot = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                    marginStart = dp(4.5f)
                    marginEnd = dp(4.5f)
                }
            }
            dots.add(dot)
            binding.layoutDots.addView(dot)
        }
        updateDots()

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateDots()
            }

            override fun onPageScrollStateChanged(state: Int) {
                // iOS `scrollViewWillBeginDragging` stops the auto-advance timer.
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    carouselHandler.removeCallbacks(autoScroll)
                    carouselStarted = false
                }
            }
        })
    }

    private fun updateDots() {
        dots.forEachIndexed { index, dot ->
            dot.setBackgroundResource(
                if (index == currentPage) R.drawable.bg_premium_dot_selected else R.drawable.bg_premium_dot_unselected
            )
        }
    }

    private fun startCarousel() {
        if (carouselStarted) return
        val reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (reduceMotion) return
        carouselStarted = true
        carouselHandler.postDelayed(autoScroll, CAROUSEL_INTERVAL_MS)
    }

    private fun setupPlans() {
        binding.rvPlans.apply {
            layoutManager = LinearLayoutManager(this@PremiumActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = planAdapter
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    if (parent.getChildAdapterPosition(view) > 0) outRect.left = dp(12)
                }
            })
        }
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnRestore.setOnClickListener { viewModel.restore() }
        binding.btnRetryPlans.setOnClickListener { viewModel.retry() }
        binding.btnContinue.setOnClickListener { continueTapped() }
        binding.cardShop.setOnClickListener {
            // iOS `openCoinShop()`; the Android coin shop isn't ported yet.
            Toast.makeText(this, "Redirecting to Coin Shop...", Toast.LENGTH_SHORT).show()
        }
        binding.btnPrivacy.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.Webview("Privacy Policy", "https://flirttime.in/page/privacy-policy"))
        }
        binding.btnTerms.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.Webview("Terms of Use", "https://flirttime.in/page/terms"))
        }
    }

    // endregion

    // region Binding

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch {
                    viewModel.options.collect { options ->
                        planAdapter.submit(options, viewModel.selectedIndex.value) { viewModel.savingsPercent(it) }
                        if (options.size != lastOptionsSize) {
                            lastOptionsSize = options.size
                            binding.rvPlans.scrollToPosition(viewModel.selectedIndex.value)
                        }
                        updateSelection()
                    }
                }
                launch {
                    combine(
                        viewModel.selectedIndex, viewModel.isSubscribed,
                        viewModel.isPurchasing, viewModel.isRestoring
                    ) { _, _, _, _ -> }.collect {
                        planAdapter.select(viewModel.selectedIndex.value)
                        updateSelection()
                    }
                }
                launch { viewModel.events.collect { handle(it) } }
            }
        }
    }

    private fun render(state: PremiumState) {
        when (state) {
            PremiumState.Loading -> {
                binding.progressPlans.visibility = View.VISIBLE
                binding.tvPlansError.visibility = View.GONE
                binding.btnRetryPlans.visibility = View.GONE
            }
            PremiumState.Loaded -> {
                binding.progressPlans.visibility = View.GONE
                binding.tvPlansError.visibility = View.GONE
                binding.btnRetryPlans.visibility = View.GONE
            }
            is PremiumState.Failed -> {
                binding.progressPlans.visibility = View.GONE
                binding.tvPlansError.text = state.message
                binding.tvPlansError.visibility = View.VISIBLE
                binding.btnRetryPlans.visibility = View.VISIBLE
            }
        }
        val loaded = state == PremiumState.Loaded
        binding.btnContinue.isEnabled = loaded
        binding.btnContinue.alpha = if (loaded) 1f else 0.6f
    }

    private fun updateSelection() {
        val option = viewModel.selectedOption
        val subscribed = viewModel.isSubscribed.value
        val purchasing = viewModel.isPurchasing.value
        val restoring = viewModel.isRestoring.value

        binding.cardComparison.visibility = if (option == null) View.GONE else View.VISIBLE
        val bonus = option?.plan?.bonusCoins ?: 0
        binding.tvBonusChip.visibility = if (bonus == 0) View.GONE else View.VISIBLE
        binding.tvBonusChip.text = "🎁 Includes ${"%,d".format(bonus)} bonus coins"

        if (option != null) {
            bindComparison(option, bonus)
            binding.tvRenewal.text = "${option.displayPrice} every ${option.plan.renewalPeriod}. " +
                "Renews automatically until cancelled. Cancel anytime in Google Play at least 24 hours before renewal."
        }

        binding.memberBanner.visibility = if (subscribed) View.VISIBLE else View.GONE
        binding.tvContinueTitle.text = when {
            subscribed -> "Manage Subscription"
            option != null -> "Get ${option.title} · ${option.displayPrice}"
            else -> "Get Premium"
        }

        val buying = purchasing && !restoring
        binding.layoutContinueContent.visibility = if (buying) View.INVISIBLE else View.VISIBLE
        binding.progressContinue.visibility = if (buying) View.VISIBLE else View.GONE
        binding.btnContinue.isClickable = !purchasing

        binding.progressRestore.visibility = if (restoring) View.VISIBLE else View.GONE
        binding.ivRestoreIcon.visibility = if (restoring) View.GONE else View.VISIBLE
        binding.btnRestore.isEnabled = !buying && !restoring
        binding.btnRestore.alpha = if (buying) 0.5f else 1f
    }

    private fun bindComparison(option: PlanOption, bonusCoins: Int) {
        binding.tvComparisonTitle.text = "Included in ${option.title}"
        val rows = binding.layoutFeatureRows
        rows.removeAllViews()
        val inflater = LayoutInflater.from(this)
        option.plan.features.orEmpty().forEachIndexed { index, feature ->
            val row = ItemPremiumFeatureRowBinding.inflate(inflater, rows, false)
            row.tvFeatureText.text = feature.text.orEmpty()
            bindFeatureIcon(row, feature.image)
            row.root.contentDescription = "${feature.text.orEmpty()}: locked on Free, included with Premium"
            if (index > 0) (row.root.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(14)
            rows.addView(row.root)
        }
        if (bonusCoins > 0) {
            val row = ItemPremiumFeatureRowBinding.inflate(inflater, rows, false)
            row.tvFeatureText.text = "$bonusCoins bonus coins"
            row.ivFeatureIcon.imageTintList = null
            row.ivFeatureIcon.layoutParams = row.ivFeatureIcon.layoutParams.apply { width = dp(26); height = dp(26) }
            row.ivFeatureIcon.setImageResource(R.drawable.ic_premium_coin)
            row.root.contentDescription = "$bonusCoins bonus coins: locked on Free, included with Premium"
            if (rows.childCount > 0) (row.root.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(14)
            rows.addView(row.root)
        }
    }

    /** iOS: a remote image when the server sends a path, otherwise an SF Symbol name (falling back to `heart.fill`). */
    private fun bindFeatureIcon(row: ItemPremiumFeatureRowBinding, image: String?) {
        if (image != null && (image.startsWith("http") || image.contains("/"))) {
            row.ivFeatureIcon.visibility = View.GONE
            row.ivFeatureRemote.visibility = View.VISIBLE
            row.ivFeatureRemote.setImageURI(MediaUrlHelper.getAlternativeUrls(image).firstOrNull())
        } else {
            row.ivFeatureRemote.visibility = View.GONE
            row.ivFeatureIcon.visibility = View.VISIBLE
            row.ivFeatureIcon.setImageResource(symbolRes(image))
        }
    }

    private fun symbolRes(name: String?): Int = when (name) {
        "eye.fill" -> R.drawable.ic_sf_eye_fill
        "infinity" -> R.drawable.ic_sf_infinity
        "bubble.left.and.bubble.right.fill" -> R.drawable.ic_sf_bubble_left_and_bubble_right_fill
        "star.fill" -> R.drawable.ic_sf_star_fill
        "gift.fill" -> R.drawable.ic_sf_gift_fill
        "bolt.heart.fill" -> R.drawable.ic_sf_bolt_heart_fill
        "crown.fill" -> R.drawable.ic_sf_crown_fill
        "lock.fill" -> R.drawable.ic_sf_lock_fill
        "sparkles" -> R.drawable.ic_sf_sparkles
        "flame.fill" -> R.drawable.ic_sf_flame_fill
        "arrow.up.heart.fill" -> R.drawable.ic_sf_arrow_up_heart_fill
        "person.2.fill" -> R.drawable.ic_sf_person_2_fill
        "phone.fill" -> R.drawable.ic_sf_phone_fill
        "video.fill" -> R.drawable.ic_sf_video_fill
        "checkmark.circle.fill" -> R.drawable.ic_sf_checkmark_circle_fill
        else -> R.drawable.ic_sf_heart_fill
    }

    // endregion

    // region Actions

    private fun continueTapped() {
        if (!viewModel.isSubscribed.value) {
            viewModel.purchaseSelected(this)
            return
        }
        // iOS `AppStore.showManageSubscriptions` → Google Play's subscription page.
        val builder = Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
            .appendQueryParameter("package", packageName)
        viewModel.selectedOption?.plan?.googleProductId?.takeIf { it.isNotBlank() }?.let {
            builder.appendQueryParameter("sku", it)
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Open Google Play › Payments & subscriptions to manage your plan.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handle(event: PurchaseEvent) {
        when (event) {
            is PurchaseEvent.Purchased -> showAlert("You're Premium 👑", event.message) { finishWithPurchase() }
            is PurchaseEvent.Restored -> showAlert("You're Premium 👑", event.message) { finishWithPurchase() }
            PurchaseEvent.Pending -> Toast.makeText(
                this,
                "Your purchase is waiting for approval. We'll unlock Premium as soon as it's confirmed.",
                Toast.LENGTH_LONG
            ).show()
            is PurchaseEvent.Failed -> showAlert("Purchase failed", event.message) { }
        }
    }

    private fun finishWithPurchase() {
        setResult(RESULT_OK)
        finish()
    }

    private fun showAlert(title: String, message: String, onDismiss: () -> Unit) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogCommonConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialogBinding.tvDialogTitle.text = title
        dialogBinding.tvDialogMessage.visibility = View.VISIBLE
        dialogBinding.tvDialogMessage.text = message
        dialogBinding.btnNegative.visibility = View.GONE
        dialogBinding.btnPositive.text = "OK"
        dialogBinding.btnPositive.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
    }

    // endregion

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    // region Adapters

    private class BenefitAdapter(private val items: List<PremiumBenefit>) :
        RecyclerView.Adapter<BenefitAdapter.Holder>() {

        class Holder(val binding: ItemPremiumBenefitBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemPremiumBenefitBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.binding.ivBenefitIcon.setImageResource(item.iconRes)
            holder.binding.tvBenefitTitle.text = item.title
            holder.binding.tvBenefitDetail.text = item.detail
            holder.binding.root.contentDescription = "${item.title}. ${item.detail}"
        }
    }

    // endregion

    companion object {
        private const val EXTRA_SOURCE = "extra_premium_source"
        private const val CAROUSEL_INTERVAL_MS = 3500L

        /** iOS `openPremium(source:)`. */
        fun start(context: Context, source: SubscriptionPopUpType? = null) {
            context.startActivity(createIntent(context, source))
        }

        fun createIntent(context: Context, source: SubscriptionPopUpType? = null): Intent =
            Intent(context, PremiumActivity::class.java).apply {
                source?.let { putExtra(EXTRA_SOURCE, it.name) }
            }
    }
}

/** iOS `PremiumPlanTile` list. */
class PremiumPlanAdapter(
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<PremiumPlanAdapter.Holder>() {

    class Holder(val binding: ItemPremiumPlanTileBinding) : RecyclerView.ViewHolder(binding.root)

    private var options: List<PlanOption> = emptyList()
    private var selected = 0
    private var savings: (PlanOption) -> Int? = { null }

    fun submit(options: List<PlanOption>, selected: Int, savings: (PlanOption) -> Int?) {
        this.options = options
        this.selected = selected
        this.savings = savings
        notifyDataSetChanged()
    }

    fun select(index: Int) {
        if (index == selected) return
        selected = index
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPremiumPlanTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = options.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val option = options[position]
        val b = holder.binding
        val (count, unit) = option.durationParts
        b.tvPlanCount.text = count
        b.tvPlanUnit.text = unit
        b.tvPlanPrice.text = option.displayPrice
        b.tvPlanWeekly.text = option.pricePerWeek ?: "billed weekly"

        val isPopular = option.plan.isPopular == true
        val saving = savings(option)
        val ribbon = if (isPopular) "MOST POPULAR" else saving?.let { "SAVE $it%" }
        b.tvPlanRibbon.visibility = if (ribbon == null) View.GONE else View.VISIBLE
        if (ribbon != null) {
            b.tvPlanRibbon.text = ribbon
            b.tvPlanRibbon.setBackgroundResource(
                if (isPopular) R.drawable.bg_premium_badge_popular else R.drawable.bg_premium_badge_savings
            )
        }

        val isSelected = position == selected
        b.cardPlan.setBackgroundResource(
            if (isSelected) R.drawable.bg_premium_tile_selected else R.drawable.bg_premium_tile_unselected
        )
        b.ivPlanCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
        b.tvPlanCount.setTextColor(Color.parseColor(if (isSelected) "#D92B2B" else "#252525"))
        b.planDivider.setBackgroundColor(Color.parseColor(if (isSelected) "#33D92B2B" else "#E3E5E5"))
        b.root.isSelected = isSelected
        b.root.contentDescription = listOfNotNull(option.title, ribbon, option.displayPrice, option.pricePerWeek)
            .joinToString(", ")
        b.root.setOnClickListener { holder.adapterPosition.takeIf { it >= 0 }?.let(onSelect) }
    }
}
