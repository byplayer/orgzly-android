package com.orgzly.android.ui.refile

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.ui.Breadcrumbs
import com.orgzly.android.ui.CommonActivity
import com.orgzly.android.usecase.BookSparseTreeForNote
import com.orgzly.android.usecase.NoteRefile
import com.orgzly.android.usecase.UseCaseRunner
import com.orgzly.android.util.LogUtils
import dagger.android.support.DaggerDialogFragment
import javax.inject.Inject

class RefileFragment : DaggerDialogFragment() {

    @Inject
    lateinit var dataRepository: DataRepository

    lateinit var viewModel: RefileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)
        super.onCreate(savedInstanceState)

        val noteIds = arguments?.getLongArray(ARG_NOTE_IDS)?.toSet() ?: emptySet()

        val factory = RefileViewModelFactory.forNotes(dataRepository, noteIds)

        viewModel = ViewModelProviders.of(this, factory).get(RefileViewModel::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)


        val dialog = object: Dialog(context!!, theme) {
            override fun onBackPressed() {
                if (viewModel.location.value?.breadcrumbs?.size ?: 0 > 1) {
                    viewModel.goUp()
                } else {
                    super.onBackPressed()
                }
            }
        }

//        val dialog = object: Dialog(context!!, theme) {
//            override fun onBackPressed() {
//                backPressed()
//            }
//        }

        return dialog
    }

//    private fun backPressed() {
//        viewModel.goUp()
//    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        val v = inflater.inflate(R.layout.dialog_refile, container, false)

        val toolbar = v.findViewById(R.id.dialog_refile_toolbar) as Toolbar

        toolbar.setTitle(R.string.refile)

        toolbar.setNavigationOnClickListener {
            dismiss()
        }

        val adapter = RefileAdapter(v.context, object: RefileAdapter.OnClickListener {
            override fun onItem(item: RefileViewModel.Item) {
                viewModel.open(item)
            }

            override fun onButton(item: RefileViewModel.Item) {
                viewModel.refile(item)
            }
        })

        v.findViewById<RecyclerView>(R.id.dialog_refile_targets).also {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = adapter
        }

//        v.findViewById<View>(R.id.dialog_refile_history).setOnClickListener {
//            viewModel.openHistory()
//        }

        val refileHereButton = v.findViewById<View>(R.id.dialog_refile_refile_here).apply {
            setOnClickListener {
                viewModel.refileHere()
            }
        }

        val breadcrumbsView = v.findViewById<TextView>(R.id.dialog_refile_breadcrumbs)
        breadcrumbsView.movementMethod = LinkMovementMethod.getInstance()

        val breadcrumbsScrollView = v.findViewById<HorizontalScrollView>(R.id.dialog_refile_breadcrumbs_scroll_view)

        viewModel.location.observe(viewLifecycleOwner, Observer { location ->
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Observed location: $location")

            adapter.submitList(location.list)

            // Hide refile-here button in notebook list
            if (location.breadcrumbs.size == 1) {
                refileHereButton.visibility = View.INVISIBLE
            } else {
                refileHereButton.visibility = View.VISIBLE
            }

            // Update and scroll breadcrumbs to the end
            breadcrumbsView.text = generateBreadcrumbs(location.breadcrumbs)
            breadcrumbsScrollView.post { breadcrumbsScrollView.fullScroll(View.FOCUS_RIGHT) }
        })

        viewModel.refiledEvent.observeSingle(viewLifecycleOwner, Observer { result ->
            dismiss()

            (result.userData as? Note)?.let { firstRefiledNote ->
                val view = activity?.findViewById<View>(R.id.main_content)

                if (view != null) {
                    (activity as CommonActivity).showSnackbar(Snackbar.make(view, firstRefiledNote.title, Snackbar.LENGTH_LONG)
                            .setAction(R.string.go_to) {
                                App.EXECUTORS.diskIO().execute {
                                    UseCaseRunner.run(BookSparseTreeForNote(firstRefiledNote.id))
                                }
                            })
                }
            }
        })

        viewModel.errorEvent.observeSingle(viewLifecycleOwner, Observer { error ->
            if (error is NoteRefile.TargetInNotesSubtree) {
                toolbar.subtitle = getString(R.string.cannot_refile_to_the_same_subtree)
            } else {
                toolbar.subtitle = (error.cause ?: error).localizedMessage
            }
        })

        viewModel.open(RefileViewModel.HOME)

        return v
    }

    private fun generateBreadcrumbs(path: List<RefileViewModel.Item>): CharSequence {
        val breadcrumbs = Breadcrumbs()

        path.forEach { item ->
            when (val payload = item.payload) {
                is RefileViewModel.Home ->
                    breadcrumbs.add(getString(R.string.notebooks)) {
                        viewModel.onBreadcrumbClick(item)
                    }

                is Book ->
                    breadcrumbs.add(payload.title ?: payload.name) {
                        viewModel.onBreadcrumbClick(item)
                    }

                is Note ->
                    breadcrumbs.add(payload.title) {
                        viewModel.onBreadcrumbClick(item)
                    }
            }
        }

        return breadcrumbs.toCharSequence()
    }

    override fun onResume() {
        super.onResume()

        dialog?.apply {

            // setCanceledOnTouchOutside(false)

            val w = resources.displayMetrics.widthPixels
            val h = resources.displayMetrics.heightPixels

            if (h > w) { // Portrait
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (h * 0.90).toInt())
            } else {
                dialog.window?.setLayout((w * 0.90).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            }

//            window?.setLayout(
//                    ViewGroup.LayoutParams.MATCH_PARENT,
//                    ViewGroup.LayoutParams.MATCH_PARENT)
        }

    }

    companion object {
        fun getInstance(noteIds: Set<Long>): RefileFragment {
            return RefileFragment().also { fragment ->
                fragment.arguments = Bundle().apply {
                    putLongArray(ARG_NOTE_IDS, noteIds.toLongArray())
                }
            }
        }

        private const val ARG_NOTE_IDS = "note_ids"

        private val TAG = RefileFragment::class.java.name

        val FRAGMENT_TAG: String = RefileFragment::class.java.name
    }
}