package com.flirttime.feature.main.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.databinding.FragmentChatBinding

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var pinnedAdapter: ChatsAdapter
    private lateinit var regularAdapter: ChatsAdapter

    private var allChats: List<ChatItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupSearch()
        loadChats()
    }

    private fun setupAdapters() {
        pinnedAdapter = ChatsAdapter { chat ->
            onChatSelected(chat)
        }

        regularAdapter = ChatsAdapter { chat ->
            onChatSelected(chat)
        }

        binding.rvPinnedChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pinnedAdapter
            setHasFixedSize(true)
        }

        binding.rvRegularChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = regularAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterChats(query)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun loadChats() {
        allChats = MockChatProvider.getChats()
        filterChats("")
    }

    private fun filterChats(query: String) {
        val matchesQuery = { chat: ChatItem ->
            if (query.isEmpty()) true
            else {
                chat.name.contains(query, ignoreCase = true) ||
                        (chat.lastMessage?.contains(query, ignoreCase = true) == true)
            }
        }

        val filteredPinned = allChats.filter { it.isPinned && matchesQuery(it) }
        val filteredRegular = allChats.filter { !it.isPinned && matchesQuery(it) }

        pinnedAdapter.submitList(filteredPinned)
        regularAdapter.submitList(filteredRegular)

        binding.layoutPinnedSection.visibility = if (filteredPinned.isNotEmpty()) View.VISIBLE else View.GONE
        binding.layoutChatsSection.visibility = if (filteredRegular.isNotEmpty()) View.VISIBLE else View.GONE

        val hasResults = filteredPinned.isNotEmpty() || filteredRegular.isNotEmpty()
        binding.layoutEmptySearch.visibility = if (hasResults) View.GONE else View.VISIBLE
    }

    private fun onChatSelected(chat: ChatItem) {
        Toast.makeText(requireContext(), "Opening chat with ${chat.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
