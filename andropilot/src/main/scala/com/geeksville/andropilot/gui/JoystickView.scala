package com.geeksville.andropilot.gui

import android.content.Context
import android.util.AttributeSet
import android.graphics._
import android.view._
import com.ridemission.scandroid.AndroidLogger

trait JoystickListener {
  def onMove(x: Float, y: Float) {}
  def onPress() {}
  def onRelease() {}
}

class JoystickView(context: Context, attrs: AttributeSet) extends View(context, attrs) with AndroidLogger {

  // center coords
  var cX = 0
  var cY = 0

  var touchX = 0
  var touchY = 0

  var ctrlRadius = 0
  var handleRadius = 0
  var movementRadius = 0

  var pointerId: Option[Int] = None

  var centerYonRelease = true
  var centerXonRelease = true
  var xLabel = "Roll"
  var yLabel = "Pitch"

  var listener = new JoystickListener {}

  val bgPaint = new Paint {
    setColor(Color.DKGRAY)
    setStrokeWidth(1)
    setStyle(Paint.Style.FILL_AND_STROKE)
  }

  val labelPaint = new Paint {
    setColor(Color.GREEN)
    setStrokeWidth(1)
    setTextSize(48)
    setStyle(Paint.Style.FILL_AND_STROKE)
  }

  val handlePaint = new Paint {
    setColor(Color.WHITE)
    setStrokeWidth(6)
    setStyle(Paint.Style.STROKE)
  }

  val selectedPaint = new Paint {
    setColor(Color.YELLOW)
    setStrokeWidth(1)
    setStyle(Paint.Style.FILL_AND_STROKE)
  }

  // We want clicks
  setClickable(true)
  setHapticFeedbackEnabled(true)

  private def drawLabels(canvas: Canvas) {
    {
      val l = "<- " + xLabel + " ->"
      canvas.drawText(l, cX - labelPaint.measureText(l) / 2, getMeasuredHeight - 40, labelPaint)
    }

    canvas.save()
    val l = "<- " + yLabel + " ->"
    val x = 30
    val y = cY - labelPaint.measureText(l) / 2
    canvas.rotate(90, x, y)
    canvas.drawText(l, x, y, labelPaint)
    canvas.restore()
  }

  override def onDraw(canvas: Canvas) {
    canvas.save()
    // Draw the background
    val rect = new RectF(0, 0, getMeasuredWidth, getMeasuredHeight)
    canvas.drawRoundRect(rect, handleRadius, handleRadius, bgPaint)

    // Draw the labels
    drawLabels(canvas)

    // Draw the handle
    val handleX = touchX + cX
    val handleY = touchY + cY

    // Fill with yellow if selected
    if (pointerId.isDefined)
      canvas.drawCircle(handleX, handleY, handleRadius, selectedPaint)

    canvas.drawCircle(handleX, handleY, handleRadius, handlePaint)

    canvas.restore()
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    cX = getMeasuredWidth / 2
    cY = getMeasuredHeight / 2

    val shortestSide = math.min(getMeasuredWidth, getMeasuredHeight)
    val pad = 4
    ctrlRadius = shortestSide / 2 - pad
    handleRadius = (shortestSide * 0.075).toInt

    movementRadius = ctrlRadius - handleRadius
  }

  def onUserRelease() {
    pointerId.foreach { id =>
      if (centerYonRelease)
        touchY = 0
      if (centerXonRelease)
        touchX = 0

      onMove()
      listener.onRelease()
      pointerId = None
      invalidate()
      performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
  }

  private def onMove() {
    listener.onMove(touchX.toFloat / cX, touchY.toFloat / cY)
  }

  private def onPress(newId: Int) {
    pointerId = Some(newId)
    listener.onPress()
  }

  override def onTouchEvent(ev: MotionEvent) = {
    val action = ev.getAction
    //debug("Got action " + action)
    (action & MotionEvent.ACTION_MASK) match {
      case MotionEvent.ACTION_MOVE =>
        processMoveEvent(ev)
        true

      case MotionEvent.ACTION_CANCEL =>
        onUserRelease()
        true

      case MotionEvent.ACTION_UP =>
        onUserRelease()
        true

      case MotionEvent.ACTION_POINTER_UP =>
        val pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT
        val newId = ev.getPointerId(pointerIndex)
        if (pointerId == Some(newId))
          onUserRelease()
        true

      case MotionEvent.ACTION_DOWN =>
        if (!pointerId.isDefined)
          onPress(ev.getPointerId(0))
        true

      case MotionEvent.ACTION_POINTER_DOWN =>
        if (!pointerId.isDefined) {
          val pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT
          val newId = ev.getPointerId(pointerIndex)
          onPress(newId)
        }
        true

      case _ =>
        false // Not for us
    }
  }

  private def clampRadius(delta: Int) =
    if (delta > movementRadius)
      movementRadius
    else if (delta < -movementRadius)
      -movementRadius
    else
      delta

  def crossedZero(oldVal: Int, newVal: Int) = oldVal.signum != newVal.signum

  def processMoveEvent(ev: MotionEvent) {
    pointerId.foreach { id =>
      val pointerIndex = ev.findPointerIndex(id)

      // Translate touch position to center of view
      val x = ev.getX(pointerIndex).toInt - cX
      val newX = clampRadius(x)
      val y = ev.getY(pointerIndex).toInt - cY
      val newY = clampRadius(y)

      if (touchX != newX || touchY != newY) {

        if (crossedZero(touchX, newX) || crossedZero(touchY, newY))
          performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        touchX = newX
        touchY = newY

        invalidate()
        onMove()
      }
    }
  }

}