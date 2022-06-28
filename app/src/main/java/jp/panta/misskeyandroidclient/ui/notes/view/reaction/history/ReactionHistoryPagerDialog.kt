package jp.panta.misskeyandroidclient.ui.notes.view.reaction.history

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import jp.panta.misskeyandroidclient.R
import jp.panta.misskeyandroidclient.databinding.DialogReactionHistoryPagerBinding
import jp.panta.misskeyandroidclient.ui.notes.viewmodel.reaction.ReactionHistoryPagerUiState
import jp.panta.misskeyandroidclient.ui.notes.viewmodel.reaction.ReactionHistoryPagerViewModel
import jp.panta.misskeyandroidclient.ui.text.CustomEmojiDecorator
import jp.panta.misskeyandroidclient.viewmodel.MiCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.notes.reaction.Reaction
import net.pantasystem.milktea.model.notes.reaction.ReactionHistoryRequest

@AndroidEntryPoint
class ReactionHistoryPagerDialog : BottomSheetDialogFragment() {

    companion object {
        private const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"
        private const val EXTRA_ACCOUNT_ID = "EXTRA_ACCOUNT_ID"
        private const val EXTRA_SHOW_REACTION_TYPE = "EXTRA_SHOW_REACTION_TYPE"

        fun newInstance(noteId: Note.Id, showReaction: String? = null): ReactionHistoryPagerDialog {
            return ReactionHistoryPagerDialog().also {
                it.arguments = Bundle().also { bundle ->
                    bundle.putString(EXTRA_NOTE_ID, noteId.noteId)
                    bundle.putLong(EXTRA_ACCOUNT_ID, noteId.accountId)
                    showReaction?.let { type ->
                        bundle.putString(EXTRA_SHOW_REACTION_TYPE, type)
                    }
                }
            }
        }
    }

    lateinit var binding: DialogReactionHistoryPagerBinding

    val pagerViewModel by viewModels<ReactionHistoryPagerViewModel>()

    private val aId: Long by lazy(LazyThreadSafetyMode.NONE) {
        requireArguments().getLong(EXTRA_ACCOUNT_ID, -1).apply {
            require(this != -1L)
        }
    }

    private val nId: String by lazy(LazyThreadSafetyMode.NONE) {
        requireArguments().getString(EXTRA_NOTE_ID)!!
    }

    private val noteId by lazy {
        Note.Id(aId, nId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.dialog_reaction_history_pager,
            container,
            false
        )
        return binding.root
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pagerViewModel.setNoteId(noteId)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val showCurrentReaction = requireArguments().getString(EXTRA_SHOW_REACTION_TYPE)


        binding.reactionHistoryTab.setupWithViewPager(binding.reactionHistoryPager)

        lifecycleScope.launchWhenCreated {
            pagerViewModel.uiState.collect { uiState ->
                val list = uiState.types
                val types = list.toMutableList().also {
                    it.add(
                        0,
                        ReactionHistoryRequest(
                            noteId,
                            null
                        )
                    )
                }
                val index = showCurrentReaction.let { type ->

                    types.indexOfFirst {
                        it.type == type
                    }
                }
                withContext(Dispatchers.Main) {
                    showPager(noteId, types)
                    binding.reactionHistoryPager.currentItem = index
                    setCustomEmojiSpanIntoTabs(uiState)
                }
            }
        }


    }

    private fun setCustomEmojiSpanIntoTabs(uiState: ReactionHistoryPagerUiState) {
        if (uiState.note != null) {
            val types = uiState.types
            for (i in 1 until types.size) {
                val tab = binding.reactionHistoryTab.getTabAt(i + 1)!!
                val textView = tab.view.children.firstOrNull {
                    it is TextView && it.id == -1
                }

                if (textView != null) {
                    val spanned = CustomEmojiDecorator().decorate(
                        uiState.note.emojis,
                        types[i].type ?: "",
                        textView,
                    )

                    tab.text = SpannableStringBuilder(spanned).apply {
                        if (Reaction(types[i].type ?: "").isCustomEmojiFormat()) {
                            append(types[i].type ?: "")
                        }
                    }
                }

            }
        }
    }

    private fun showPager(noteId: Note.Id, types: List<ReactionHistoryRequest>) {
        val adapter = ReactionHistoryPagerAdapter(childFragmentManager, types, noteId)
        binding.reactionHistoryPager.adapter = adapter
    }

    override fun onPause() {
        super.onPause()
        dismissAllowingStateLoss()
    }

    override fun onDestroy() {
        super.onDestroy()
        val miCore = requireContext().applicationContext as MiCore
        requireActivity().lifecycleScope.launch(Dispatchers.IO) {
            miCore.getReactionHistoryDataSource().clear(noteId)
        }
    }
}