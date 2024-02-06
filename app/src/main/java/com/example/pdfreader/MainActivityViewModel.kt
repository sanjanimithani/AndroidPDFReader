package com.example.pdfreader

import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import androidx.lifecycle.*
import java.util.*
class MainActivityViewModel(private val state: SavedStateHandle) :  ViewModel() {
    var pageNum: Int = 0
    var allowDraw: Boolean = false
    var allowHighlight: Boolean = false
    var erase: Boolean = false
    var drawingsPathsPerPage: MutableMap<Int, MutableList<MutableList<Path?>>> = mutableMapOf()
    var highlighterPathsPerPage: MutableMap<Int, MutableList<MutableList<Path?>>> = mutableMapOf()
    var undoStack: MutableMap<Int, Stack<Pair<PDFimage.Style, MutableList<Path?>>>> = mutableMapOf()
    var redoStack: MutableMap<Int, Stack<Pair<PDFimage.Style, MutableList<Path?>>>> = mutableMapOf()

    val LOGNAME = "viewmodel"

    fun addDrawPaths(newPath: MutableList<Path?>) {
        val current = drawingsPathsPerPage[pageNum]
        Log.d(LOGNAME, "pageNum: $pageNum")

        if (current == null) {
            val listValue = mutableListOf<MutableList<Path?>>()
            listValue.add(newPath)
            drawingsPathsPerPage!![pageNum] = listValue
            Log.d(LOGNAME, "Draw 1")
        } else {
            current.add(newPath)
            drawingsPathsPerPage!![pageNum] = current
            Log.d(LOGNAME, "Draw 2")
        }
    }

    fun addHighlightPaths(newPath: MutableList<Path?>) {
        val current = highlighterPathsPerPage[pageNum]

        Log.d(LOGNAME, "pageNum: $pageNum")
        if (current == null) {
            val listValue = mutableListOf<MutableList<Path?>>()
            listValue.add(newPath)
            highlighterPathsPerPage!![pageNum] = listValue
            Log.d(LOGNAME, "Highlight 1")
        } else {
            current.add(newPath)
            highlighterPathsPerPage!![pageNum] = current
            Log.d(LOGNAME, "Highlight 2")
        }
    }

    fun erase(x: Float, y: Float) {
        val pointRect = RectF(x, y, x, y)

        Log.d(LOGNAME, "Erase")

        var current = drawingsPathsPerPage[pageNum]

        if (current != null) {
            for (d in drawingsPathsPerPage!![pageNum]!!) {
                for (p in d!!) {
                    val bounds = RectF()
                    p!!.computeBounds(bounds, true)
                    if (bounds.contains(pointRect)) {
                        Log.d(LOGNAME, "Found Point")
                        addUndoAction(Pair(PDFimage.Style.ERASE_DRAW, d))
                        clearRedoStack()
                        drawingsPathsPerPage[pageNum]?.remove(d)
                        return;
                    }
                }
            }
        }

        current = highlighterPathsPerPage[pageNum]

        if (current != null) {
            for (d in highlighterPathsPerPage!![pageNum]!!) {
                for (p in d!!) {
                    val bounds = RectF()
                    p!!.computeBounds(bounds, true)
                    if (bounds.contains(pointRect)) {
                        Log.d(LOGNAME, "Found Point")
                        addUndoAction(Pair(PDFimage.Style.ERASE_HIGHLIGHT, d))
                        clearRedoStack()
                        highlighterPathsPerPage[pageNum]?.remove(d)
                        return;
                    }
                }
            }
        }
    }

    fun addUndoAction(action: Pair<PDFimage.Style, MutableList<Path?>>) {
       var stack = undoStack[pageNum]

        if (stack == null) {
            stack = Stack()
        }
        stack.push(action)
        undoStack[pageNum] = stack
    }

    fun addRedoAction(action: Pair<PDFimage.Style, MutableList<Path?>>) {
        var stack = redoStack[pageNum]

        if (stack == null) {
            stack = Stack()
        }
        stack.push(action)
        redoStack[pageNum] = stack
    }

    fun clearRedoStack() {
        redoStack[pageNum] = Stack()
    }

}