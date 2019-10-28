package `in`.championswimmer.imgurapp

import `in`.championswimmer.imgurapp.enums.FetchStatus.FAILED
import `in`.championswimmer.imgurapp.enums.FetchStatus.FETCHING
import `in`.championswimmer.imgurapp.enums.FetchStatus.NONE
import `in`.championswimmer.imgurapp.enums.FetchStatus.SUCCESS
import `in`.championswimmer.imgurapp.utils.ImageDownloader.initiateImageDownload
import `in`.championswimmer.imgurapp.utils.ImageSharer.initiateImageShare
import `in`.championswimmer.imgurapp.viewmodels.PhotoStoryViewModel
import `in`.championswimmer.libimgur.models.Image
import android.animation.Animator
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    companion object {
        const val PERM_REQ_CODE = 1451
    }

    lateinit var photoStoryViewModel: PhotoStoryViewModel
    var currentAnimator: ObjectAnimator? = null

    private fun setupViewModel() {
        photoStoryViewModel = ViewModelProviders.of(this).get(PhotoStoryViewModel::class.java)
        photoStoryViewModel.fetchStatus.observe(this, Observer {
            when (it) {
                FETCHING -> contentLoader.show()
                SUCCESS -> contentLoader.hide()
                FAILED -> {
                    contentLoader.hide()
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.err_fetch_story_title))
                        .setMessage(getString(R.string.err_fetch_story_msg))
                        .setNegativeButton(getString(R.string.btn_close_app)) { _, _ -> finish() }
                        .setPositiveButton(getString(R.string.btn_retry)) { _, _ -> refresh() }
                        .setCancelable(false)
                        .show()
                }
            }
        })

        photoStoryViewModel.photoStream.observe(this, Observer {
            goToNextPhoto()
        })
    }

    private fun refresh() {
        photoStoryViewModel.fetchStatus.value = NONE
        photoStoryViewModel.refreshPhotoStory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViewModel()
        refresh()

    }

    /**
     * A function-calling-function to prevent recursion inside [goToNextPhoto]
     */
    private val callGoToNext = { it: Animator -> goToNextPhoto() }

    private fun goToNextPhoto() {
        if (photoStoryViewModel.photoStream.value?.empty() == true) {
            photoStoryViewModel.refreshPhotoStory(true)
        }
        photoStoryViewModel.photoStream.value?.pop()?.let { image ->
            Glide.with(ivPhotoStory).load(image.link).into(ivPhotoStory)
            tvPhotoTitle.text = image.title

            ivPhotoStory.setOnClickListener {
                image.parentItemId?.let { hash -> AlbumDetailsActivity.start(this, hash) }
            }
            ivPhotoStory.setOnLongClickListener {
                showDetailDialog(image)
                true
            }

            currentAnimator = ObjectAnimator.ofInt(progressPhotoStory, "progress", 0, 100).apply {
                duration = 4000
                interpolator = LinearInterpolator()
                start()
                doOnEnd(callGoToNext)
            }

            // Preload the next photo

            photoStoryViewModel.photoStream.value?.peek()?.let {
                Glide.with(ivPhotoStory).load(it.link).preload()
            }
        }

    }

    private fun showDetailDialog(image: Image) {
        currentAnimator?.takeIf { it.isStarted && it.isRunning }?.pause()

        AlertDialog.Builder(this)
            .setTitle(image.title)
            .setMessage(image.description ?: "")
            .setIcon(android.R.drawable.ic_menu_gallery)
            .setOnDismissListener {
                currentAnimator?.takeIf { it.isStarted && it.isPaused }?.resume()

            }
            .setNeutralButton("Download") { _, _ ->
                initiateImageDownload(this, image)
            }
            .setNegativeButton("Share") { _, _ ->
                initiateImageShare(this, image)
            }
            .setPositiveButton("Like") { _, _ -> }
            .show()
    }

    override fun onResume() {
        super.onResume()
        currentAnimator?.takeIf { it.isStarted && it.isPaused }?.resume()
    }

    override fun onPause() {
        currentAnimator?.takeIf { it.isStarted && it.isRunning }?.pause()
        super.onPause()
    }

}
