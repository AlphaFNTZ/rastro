package com.example.rastro

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.PagerAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class PaginasPrincipaisTest {
    private fun withPager(test: (PaginasPrincipais,List<FrameLayout>) -> Unit) {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity=controller.get()
            val pager=PaginasPrincipais(activity)
            val pages=List(3) { FrameLayout(activity) }
            pager.offscreenPageLimit=2
            pager.adapter=object : PagerAdapter() {
                override fun getCount()=3
                override fun isViewFromObject(view: View, item: Any)=view===item
                override fun instantiateItem(container: ViewGroup, position: Int): Any =
                    pages[position].also { if(it.parent==null) container.addView(it) }
                override fun destroyItem(container: ViewGroup, position: Int, item: Any) { container.removeView(item as View) }
            }
            // Same eager attachment used by MainActivity's map/control initialization.
            pages.forEach { pager.addView(it) }
            activity.setContentView(pager)
            pager.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY))
            pager.layout(0,0,1000,1600)
            test(pager,pages)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun pagesAreRetainedAndCanBeVisitedInEitherDirection() = withPager { pager,pages ->
        pages[1].tag="preserved"
        for(index in listOf(2,1,0,1,2,0)) {
            pager.setCurrentItem(index,false)
            assertEquals(index,pager.currentItem)
            assertEquals(index*1000,pager.scrollX)
            assertSame(pager,pages[index].parent)
        }
        assertEquals("preserved",pages[1].tag)
    }
    @Test fun partialDragMovesContinuouslyAndReversesBeforeRelease() = withPager { pager,_ ->
        pager.setCurrentItem(1,false)
        assertTrue(pager.beginFakeDrag())
        pager.fakeDragBy(-240f)
        assertEquals(1240,pager.scrollX)
        pager.fakeDragBy(100f)
        assertEquals(1140,pager.scrollX)
        pager.endFakeDrag()
    }
    @Test fun dragAtFirstAndLastPageDoesNotWrapAround() = withPager { pager,_ ->
        assertTrue(pager.beginFakeDrag()); pager.fakeDragBy(500f)
        assertEquals(0,pager.scrollX); pager.endFakeDrag()
        pager.setCurrentItem(2,false)
        assertTrue(pager.beginFakeDrag()); pager.fakeDragBy(-500f)
        assertEquals(2000,pager.scrollX); pager.endFakeDrag()
    }
    @Test fun mapSurfaceKeepsHorizontalGesture() = withPager { pager,pages ->
        val map=View(pager.context).apply { id=R.id.map_host }
        pages[0].addView(map,FrameLayout.LayoutParams(-1,-1))
        pages[0].measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY))
        pages[0].layout(0,0,1000,1600)
        val area=android.graphics.Rect(); assertTrue(map.getGlobalVisibleRect(area))
        val down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,area.exactCenterX(),area.exactCenterY(),0)
        val move=MotionEvent.obtain(0,100,MotionEvent.ACTION_MOVE,area.exactCenterX()-200f,area.exactCenterY(),0)
        try {
            assertFalse(pager.onInterceptTouchEvent(down))
            assertFalse(pager.onInterceptTouchEvent(move))
            assertEquals(0,pager.scrollX)
        } finally { down.recycle(); move.recycle() }
    }
}