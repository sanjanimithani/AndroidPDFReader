package com.example.pdfreader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PersistableBundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.
class MainActivity : AppCompatActivity() {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage

    // create view model using delegation
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val layout = findViewById<LinearLayout>(R.id.pdfLayout)
        layout.isEnabled = true
        pageImage = PDFimage(this, viewModel)

        layout.addView(pageImage)

        // Set the desired width to the ImageView
        pageImage.minimumWidth = 1000
        pageImage.minimumHeight = 2000

        showPDFName()
        if (savedInstanceState != null) {
            try {
                openRenderer(this)
                viewModel.pageNum = savedInstanceState.getInt("pageNum", 0)
                showPage(viewModel.pageNum)
                showNumPages(viewModel.pageNum)
            } catch (exception: IOException) {
                Log.d(LOGNAME, "Error opening PDF")
            }
        } else {
            // it will be displayed as an image in the pageImage (above)
            try {
                openRenderer(this)
                showPage(0)
                showNumPages(0)
            } catch (exception: IOException) {
                Log.d(LOGNAME, "Error opening PDF")
            }
        }

        // Ensures that when drawing is enabled, then scrolling is disabled
        /*val scrollView = findViewById<ScrollView>(R.id.scrollview)
        scrollView.setOnTouchListener { view, motionEvent ->
            if (viewModel.allowDraw) {
                pageImage.dispatchTouchEvent(motionEvent)
            } else {
                scrollView.onTouchEvent(motionEvent)
            }
        }*/

    }

    override fun onDestroy() {
        super.onDestroy()
        try {

            closeRenderer()
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error closing PDF")
        }
    }

    /*override fun onStop() {
        super.onStop()
        try {
            closeRenderer()
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error closing PDF")
        }
    }*/

    fun pageForward(view: View) {
        if ( viewModel.pageNum + 1 < pdfRenderer.pageCount) {
            viewModel.pageNum += 1
        }
        showPage(viewModel.pageNum)
        showNumPages(viewModel.pageNum)

    }

    fun pageBack(view: View) {
        if ( viewModel.pageNum - 1 >= 0) {
            viewModel.pageNum -= 1
        }
        showPage(viewModel.pageNum)
        showNumPages(viewModel.pageNum)
    }

    fun drawBtnClicked(view: View) {
        viewModel.allowHighlight = false
        viewModel.erase = false
        viewModel.allowDraw = !viewModel.allowDraw
        pageImage.setPaint()
        Log.d(LOGNAME, "Allow Draw Value: ${viewModel.allowDraw}")

        val mode = findViewById<TextView>(R.id.mode)
        if(viewModel.allowDraw) {
            mode.text = "Mode: Draw"
        } else {
            mode.text = "Mode: Pan"
        }
    }
    fun highlightBtnClicked(view: View) {
        viewModel.allowDraw = false
        viewModel.erase = false
        pageImage.setPaint()
        viewModel.allowHighlight = !viewModel.allowHighlight
        Log.d(LOGNAME, "Allow Highlight Value: ${viewModel.allowHighlight}")

        val mode = findViewById<TextView>(R.id.mode)
        if(viewModel.allowHighlight) {
            mode.text = "Mode: Highlight"
        } else {
            mode.text = "Mode: Pan"
        }
    }

    fun eraseBtnClicked(view: View) {
        viewModel.allowDraw = false
        viewModel.allowHighlight = false
        viewModel.erase = !viewModel.erase
        Log.d(LOGNAME, "Erase Value: ${viewModel.erase}")

        val mode = findViewById<TextView>(R.id.mode)
        if(viewModel.erase) {
            mode.text = "Mode: Erase"
        } else {
            mode.text = "Mode: Pan"
        }
    }

    fun undoBtnClicked(view: View) {
        pageImage.undo()
    }

    fun redoBtnClicked(view: View) {
        pageImage.redo()
    }

    private fun showPDFName() {
        val title = findViewById<TextView>(R.id.pdfName)
        title.text = FILENAME
    }

    private fun showNumPages(currPage: Int) {
        val pageCount = pdfRenderer.pageCount
        val numberOfPagesTextView = findViewById<TextView>(R.id.numPagesTextView)
        numberOfPagesTextView.text = " Page ${currPage+1} / $pageCount"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pageNum", viewModel.pageNum)
        outState.putInt("currentPage", viewModel.pageNum)
        outState.putBoolean("allowDraw", viewModel.allowDraw)
        outState.putBoolean("allowHighlight", viewModel.allowHighlight)
        outState.putBoolean("allowErase", viewModel.erase)

    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onRestoreInstanceState(savedInstanceState, persistentState)
    }


    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {

        if (pdfRenderer.pageCount <= index) {
            return
        }
        // Close the current page before opening another one.
        currentPage?.close()

        // Use `openPage` to open a specific page in PDF.
        viewModel.pageNum = index
        currentPage = pdfRenderer.openPage(index)

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).
            val bitmap = Bitmap.createBitmap(currentPage!!.getWidth(), currentPage!!.getHeight(), Bitmap.Config.ARGB_8888)

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)


            // Clear the paths and restore the ones that were there
            pageImage.setDrawings()
            pageImage.setHighlights()
            Log.d(LOGNAME, "calling the methods")

            // Display the page
            pageImage.setImage(bitmap)

        }
    }

}