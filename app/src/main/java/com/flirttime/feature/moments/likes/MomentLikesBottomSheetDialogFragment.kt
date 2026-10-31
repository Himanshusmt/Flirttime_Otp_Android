package com.flirttime.feature.moments.likes

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.Resource
import com.flirttime.core.common.IsoDate
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.BottomSheetMomentLikesBinding
import com.flirttime.databinding.ItemMomentLikeBinding
import com.flirttime.feature.moments.MomentLikeDto
import com.flirttime.feature.moments.MomentsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * People who liked a moment, newest first, loaded page by page from `GET moments/{id}/likes`
 * (iOS `MomentLikesViewController`).
 */
class MomentLikesBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "MomentLikesBottomSheet"
        private const val ARG_MOMENT_ID = "arg_moment_id"
        private const val PAGE_SIZE = 30

        fun newInstance(momentId: String) = MomentLikesBottomSheetDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_MOMENT_ID, momentId) }
        }
    }

    private var _binding: BottomSheetMomentLikesBinding? = null
    private val binding get() = _binding!!
    private val momentsRepository: MomentsRepository by inject()

    private val adapter = LikesAdapter()
    private var momentId = ""
    private var page = 0
    private var totalPages = 1
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        momentId = arguments?.getString(ARG_MOMENT_ID).orEmpty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                it.setBackgroundResource(android.R.color.transparent)
                BottomSheetBehavior.from(it).apply {
                    skipCollapsed = true
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetMomentLikesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvLikes.layoutManager = layoutManager
        binding.rvLikes.adapter = adapter
        binding.rvLikes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 5) loadNextPage()
            }
        })
        loadNextPage()
    }

    private fun loadNextPage() {
        if (isLoading || page >= totalPages) return
        isLoading = true
        if (adapter.itemCount == 0) binding.progressLikes.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            momentsRepository.getMomentLikes(momentId, page + 1, PAGE_SIZE).collect { resource ->
                when (resource) {
                    is Resource.Loading -> Unit
                    is Resource.Success -> {
                        isLoading = false
                        binding.progressLikes.visibility = View.GONE
                        val data = resource.data
                        page = data.page ?: (page + 1)
                        totalPages = data.totalPages ?: page
                        adapter.addAll(data.likes.orEmpty())
                        val total = maxOf(data.totalLikes ?: 0, adapter.itemCount)
                        binding.tvLikesSummary.text = "$total ${if (total == 1) "like" else "likes"}"
                        binding.tvLikesEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
                    }
                    is Resource.Error -> {
                        isLoading = false
                        binding.progressLikes.visibility = View.GONE
                        if (adapter.itemCount == 0) {
                            binding.tvLikesEmpty.text = resource.message ?: "Couldn't load likes. Please try again."
                            binding.tvLikesEmpty.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class LikesAdapter : RecyclerView.Adapter<LikesAdapter.Holder>() {
        private val items = mutableListOf<MomentLikeDto>()

        fun addAll(more: List<MomentLikeDto>) {
            val ids = items.mapNotNull { it.id }.toHashSet()
            val fresh = more.filter { it.id == null || it.id !in ids }
            val start = items.size
            items.addAll(fresh)
            notifyItemRangeInserted(start, fresh.size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemMomentLikeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        class Holder(private val b: ItemMomentLikeBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(like: MomentLikeDto) {
                val user = like.user
                b.tvLikeName.text = user?.displayName ?: "Someone"
                b.ivLikeVerified.visibility = if (user?.isVerified == true) View.VISIBLE else View.GONE
                val city = user?.city.orEmpty()
                b.tvLikeCity.text = city
                b.tvLikeCity.visibility = if (city.isBlank()) View.GONE else View.VISIBLE
                b.tvLikeTime.text = IsoDate.timeAgo(like.createdAt)
                val photo = user?.photo
                if (photo.isNullOrBlank()) {
                    b.ivLikeAvatar.setActualImageResource(com.flirttime.R.drawable.img_login)
                } else {
                    b.ivLikeAvatar.setImageURI(MediaUrlHelper.resolveMediaUrl(photo))
                }
            }
        }
    }
}
