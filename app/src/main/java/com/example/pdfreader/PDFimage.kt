package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView.Orientation

@SuppressLint("AppCompatCustomView")
class PDFimage (context: Context?, private val viewModel: MainActivityViewModel) : ImageView(context) {

    val LOGNAME = "pdf_image"

    // drawing path
    var path: Path? = null
    var paths = mutableListOf<Path?>()
    var curr = mutableListOf<Path?>()

    var highlighterPaths = mutableListOf<Path?>()

    // image to display
    var bitmap: Bitmap? = null
    var paint = Paint(Color.BLUE)

    enum class Style {
        DRAW,
        HIGHLIGHT,
        ERASE_DRAW,
        ERASE_HIGHLIGHT
    }

    // we save a lot of points because they need to be processed
    // during touch events e.g. ACTION_MOVE
    var x1 = 0f
    var x2 = 0f
    var y1 = 0f
    var y2 = 0f
    var old_x1 = 0f
    var old_y1 = 0f
    var old_x2 = 0f
    var old_y2 = 0f
    var mid_x = -1f
    var mid_y = -1f
    var old_mid_x = -1f
    var old_mid_y = -1f
    var p1_id = 0
    var p1_index = 0
    var p2_id = 0
    var p2_index = 0

    // store cumulative transformations
    // the inverse matrix is used to align points with the transformations - see below
    var currentMatrix = Matrix()
    var inverse = Matrix()
    var scaledBitmap = bitmap

    fun setDrawings() {
        val drawings = viewModel.drawingsPathsPerPage[viewModel.pageNum]
        paths = mutableListOf()
        if (drawings != null) {
            Log.d(LOGNAME, "Size: ${drawings.size}")
            for (d in drawings) {
                paths.addAll(d)
            }
        }
        invalidate()
    }

    fun setHighlights() {
        val drawings = viewModel.highlighterPathsPerPage[viewModel.pageNum]
        highlighterPaths = mutableListOf()
        if (drawings != null) {
            for (d in drawings) {
                highlighterPaths.addAll(d)
            }
        }
        invalidate()
    }

    fun undo() {

        if (viewModel.undoStack.isEmpty()) {
            Log.d(LOGNAME, "Nothing to  Undo")
            return
        }

        val stack = viewModel.undoStack[viewModel.pageNum]

        if (stack == null || stack.isEmpty()) {
            Log.d(LOGNAME, "Nothing to  Undo")
            return
        }

        val action = stack.pop()

        // Undo Draw --> Erase Draw
        if (action.first == Style.DRAW) {
            Log.d(LOGNAME, "Undo Draw")
            viewModel.drawingsPathsPerPage[viewModel.pageNum]?.remove(action.second)
            viewModel.addRedoAction(action)
            setDrawings()
        }
        // Undo Highlight --> Erase Highlight
        else if (action.first == Style.HIGHLIGHT) {
            Log.d(LOGNAME, "Undo Highlight")
            viewModel.highlighterPathsPerPage[viewModel.pageNum]?.remove(action.second)
            viewModel.addRedoAction(action)
            setHighlights()
        }
        // Undo Erase Draw --> Draw
        else if (action.first == Style.ERASE_DRAW) {
            Log.d(LOGNAME, "Undo Erase Draw")
            viewModel.addDrawPaths(action.second)
            viewModel.addRedoAction(action)
            setDrawings()
        }
        // Undo Erase Highlight --> Highlight
        else if (action.first == Style.ERASE_HIGHLIGHT) {
            Log.d(LOGNAME, "Undo Erase Highlight")
            viewModel.addHighlightPaths(action.second)
            viewModel.addRedoAction(action)
            setHighlights()
        }
    }

    fun redo() {

        if (viewModel.redoStack.isEmpty()) {
            Log.d(LOGNAME, "Nothing to  Undo")
            return
        }

        val stack = viewModel.redoStack[viewModel.pageNum]

        if (stack == null || stack.isEmpty()) {
            Log.d(LOGNAME, "Nothing to  Undo")
            return
        }

        val action = stack.pop()

        // Redo Draw
        if (action.first == Style.DRAW) {
            Log.d(LOGNAME, "Redo Draw")
            viewModel.addDrawPaths(action.second)
            viewModel.addUndoAction(action)
            setDrawings()
        }
        // Undo Highlight --> Erase Highlight
        else if (action.first == Style.HIGHLIGHT) {
            Log.d(LOGNAME, "Redo Highlight")
            viewModel.addHighlightPaths(action.second)
            viewModel.addUndoAction(action)
            setHighlights()
        }
        // Redo Erase Draw
        else if (action.first == Style.ERASE_DRAW) {
            Log.d(LOGNAME, "Undo Erase Draw")
            viewModel.drawingsPathsPerPage[viewModel.pageNum]?.remove(action.second)
            viewModel.addUndoAction(action)
            setDrawings()
        }
        // Redo Erase Highlight
        else if (action.first == PDFimage.Style.ERASE_HIGHLIGHT) {
            Log.d(LOGNAME, "Undo Erase Highlight")
            viewModel.highlighterPathsPerPage[viewModel.pageNum]?.remove(action.second)
            viewModel.addUndoAction(action)
            setHighlights()
        }
    }

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {

        var inverted = floatArrayOf()
        //currentMatrix.reset()


        when (event.pointerCount) {
            // 1-finger
            1 -> {

                // Getting the coordinates for zoom **********************************
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // invert using the current matrix to account for pan/scale
                // inverts in-place and returns boolean
                inverse = Matrix()
                currentMatrix.invert(inverse)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)
                x1 = inverted[0]
                y1 = inverted[1]


                // Drawing Code ******************************************************
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (viewModel.allowDraw || viewModel.allowHighlight) {
                            path = Path()
                            path!!.moveTo(x1, y1)
                            curr = mutableListOf<Path?>()
                        }

                        old_x1 = x1
                        old_y1 = y1

                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (viewModel.allowDraw || viewModel.allowHighlight) {
                            path!!.lineTo(x1, y1)
                            curr.add(path)

                            if (viewModel.allowDraw) {
                                paths.add(path)
                            } else if (viewModel.allowHighlight) {
                                highlighterPaths.add(path)
                            }
                        }

                        if (viewModel.erase) {
                            viewModel.erase(x1, y1)
                            setDrawings()
                            setHighlights()
                        }

                        if (!viewModel.allowDraw && !viewModel.allowHighlight && !viewModel.erase) {
                            val dx = x1 - old_x1
                            val dy = y1 - old_y1
                            currentMatrix.preTranslate(dx, dy)
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (viewModel.erase) {
                            viewModel.erase(x1, y1)
                            setDrawings()
                            setHighlights()
                        }
                        if (viewModel.allowDraw) {
                            viewModel.addDrawPaths(curr)
                            viewModel.addUndoAction(Pair(Style.DRAW, curr))
                            viewModel.clearRedoStack()
                        } else if (viewModel.allowHighlight) {
                            viewModel.addHighlightPaths(curr)
                            viewModel.addUndoAction(Pair(Style.HIGHLIGHT, curr))
                            viewModel.clearRedoStack()
                        }

                        old_x1 = -1f
                        old_y1 = -1f
                    }
                }
            }

            // 2-fingers
            2 -> {
                // point 1
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x1 < 0 || old_y1 < 0) {
                    x1 = inverted.get(0)
                    old_x1 = x1
                    y1 = inverted.get(1)
                    old_y1 = y1
                } else {
                    old_x1 = x1
                    old_y1 = y1
                    x1 = inverted.get(0)
                    y1 = inverted.get(1)
                }

                // point 2
                p2_id = event.getPointerId(1)
                p2_index = event.findPointerIndex(p2_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p2_index), event.getY(p2_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x2 < 0 || old_y2 < 0) {
                    x2 = inverted.get(0)
                    old_x2 = x2
                    y2 = inverted.get(1)
                    old_y2 = y2
                } else {
                    old_x2 = x2
                    old_y2 = y2
                    x2 = inverted.get(0)
                    y2 = inverted.get(1)
                }

                // midpoint
                mid_x = (x1 + x2) / 2
                mid_y = (y1 + y2) / 2
                old_mid_x = (old_x1 + old_x2) / 2
                old_mid_y = (old_y1 + old_y2) / 2

                // distance
                val d_old =
                    Math.sqrt(Math.pow((old_x1 - old_x2).toDouble(), 2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                        .toFloat()
                val d = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                    .toFloat()

                // pan and zoom during MOVE event
                if (event.action == MotionEvent.ACTION_MOVE) {
                    // zoom == change of spread between p1 and p2
                    var scale = d / d_old
                    scale = Math.max(0f, scale)
                    currentMatrix.preScale(scale, scale, mid_x, mid_y)

                    // reset on up
                } else if (event.action == MotionEvent.ACTION_UP) {
                    old_x1 = -1f
                    old_y1 = -1f
                    old_x2 = -1f
                    old_y2 = -1f
                    old_mid_x = -1f
                    old_mid_y = -1f
                    invalidate()
                }
            }
            else -> {
            }
        }
        return true
    }

    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val orientation = context.resources.configuration.orientation

        val width = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        val height = MeasureSpec.getSize(heightMeasureSpec).toFloat()

        val bitmapWidth = this.bitmap!!.width.toFloat()
        val bitmapHeight = this.bitmap!!.height.toFloat()
        val aspectRatio = bitmapWidth / bitmapHeight
        val newHeight =  width / aspectRatio

        val mValues = FloatArray(9)
        val curr = currentMatrix.getValues(mValues)
        val currScale = mValues[Matrix.MSCALE_X]

        //Option 1
        //scaledBitmap = Bitmap.createScaledBitmap(bitmap!!, width.toInt(), newHeight.toInt(), true)

        //Option 2
        scaledBitmap = Bitmap.createScaledBitmap(bitmap!!, 1800, 2167, true)

        currentMatrix.reset()
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val scale = 2560.0 / 1800.0
            Log.d(LOGNAME, "scaleY: ${scale}")
            currentMatrix.preScale(scale.toFloat(), scale.toFloat())
        }

        setMeasuredDimension(width.toInt(), newHeight.toInt())
    }


    // set brush characteristics
    // e.g. color, thickness, alpha
    fun setBrush(paint: Paint) {
        this.paint.strokeWidth = 5.0f
        this.paint.style = Paint.Style.STROKE
        this.paint.color = Color.BLACK
        this.paint = paint
    }

    fun setHighlightBrush(paint: Paint) {
        this.paint.strokeWidth = 20.0f
        this.paint.style = Paint.Style.STROKE
        this.paint.color = Color.YELLOW
        this.paint.alpha = 5
        this.paint = paint
    }

    fun setPaint() {
        if (viewModel.allowHighlight) {
            setHighlightBrush(paint)
        } else if (viewModel.allowDraw) {
            setBrush(paint)
        }
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        // apply transformations from the event handler above
        canvas.concat(currentMatrix)

        // draw background
        if (scaledBitmap != null) {
                // Option 1
                //setImageBitmap(scaledBitmap)

                //Option 2
                val new = Bitmap.createBitmap(scaledBitmap!!.height, scaledBitmap!!.width, Bitmap.Config.ARGB_8888)
                setImageBitmap(new)
                canvas.drawBitmap(scaledBitmap!!, matrix, null)

        }

        // draw lines over it
        setBrush(paint)
        for (path in paths) {
            path?.let { canvas.drawPath(it, paint) }
        }

        setHighlightBrush(paint)
        for (path in highlighterPaths) {
            path?.let { canvas.drawPath(it, paint) }
        }
        //currentMatrix.reset()
    }
}