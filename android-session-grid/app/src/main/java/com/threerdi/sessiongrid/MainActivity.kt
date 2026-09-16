package com.threerdi.sessiongrid

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.decorView.systemUiVisibility = 5894; setContentView(SessionView(this)) }
}

class SessionView(ctx: android.content.Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tracks = arrayOf("KICK","DRUMS","BASS","PERC","SYNTH","DRONE","FX","VOX")
    private val colors = intArrayOf(0xffd65b43.toInt(),0xffe08b39.toInt(),0xffd1b13c.toInt(),0xff65a86d.toInt(),0xff4f9fbf.toInt(),0xff7772c5.toInt(),0xffa963b3.toInt(),0xff777777.toInt())
    private val active = BooleanArray(8)
    private val armed = BooleanArray(8)
    private var selected = -1
    private var bpm = 133
    private var playing = true
    private val scenes = 8

    override fun onDraw(c: Canvas) {
        super.onDraw(c); c.drawColor(Color.rgb(28,28,28)); val w=width.toFloat(); val h=height.toFloat();
        val top=76f; val bottom=88f; val left=64f; val right=72f; val gap=4f; val tw=(w-left-right-gap*7)/8f; val sh=(h-top-bottom-gap*7)/8f
        p.typeface=Typeface.DEFAULT_BOLD; p.textAlign=Paint.Align.LEFT; p.textSize=22f; p.color=0xffeeeeee.toInt(); c.drawText("3rdi SESSION GRID",18f,31f,p)
        p.textSize=14f; p.color=0xffa0a0a0.toInt(); c.drawText("$bpm BPM    4/4    1 BAR",18f,55f,p)
        p.textAlign=Paint.Align.CENTER
        for(t in 0..7){ val x=left+t*(tw+gap); p.color=0xff3b3b3b.toInt(); c.drawRect(x,top-35,x+tw,top-5,p); p.color=0xffe8e8e8.toInt(); p.textSize=12f; c.drawText(tracks[t],x+tw/2,top-15,p)
            for(s in 0 until scenes){ val y=top+s*(sh+gap); val r=RectF(x,y,x+tw,y+sh); p.color=if(active[t]&&selected==s) colors[t] else if((s+t)%3==0) Color.rgb(73,73,73) else Color.rgb(51,51,51); c.drawRoundRect(r,3f,3f,p); p.style=Paint.Style.STROKE; p.strokeWidth=1f; p.color=0xff686868.toInt(); c.drawRoundRect(r,3f,3f,p); p.style=Paint.Style.FILL; p.color=0xffcfcfcf.toInt(); p.textSize=10f; c.drawText(if((s+t)%3==0) "${tracks[t]} ${s+1}" else "",r.centerX(),r.centerY()+4,p) }
            val fy=h-bottom+14; p.color=0xff343434.toInt(); c.drawRect(x,fy,x+tw,fy+58,p); p.color=if(armed[t])0xffe65353.toInt() else 0xff555555.toInt(); c.drawCircle(x+tw*.2f,fy+17,7f,p); p.color=0xff999999.toInt(); c.drawCircle(x+tw*.5f,fy+17,7f,p); p.color=0xff999999.toInt(); c.drawCircle(x+tw*.8f,fy+17,7f,p); p.color=colors[t]; c.drawRect(x+8,fy+34,x+tw-8,fy+41,p) }
        for(s in 0 until scenes){ val y=top+s*(sh+gap); p.color=0xff444444.toInt(); c.drawCircle(w-35,y+sh/2,14f,p); p.color=0xffdddddd.toInt(); p.textSize=10f; c.drawText("▶",w-35,y+sh/2+4,p) }
        p.color=if(playing)0xff73b66f.toInt() else 0xff555555.toInt(); c.drawRoundRect(RectF(w-185,12,w-125,58),5f,5f,p); p.color=0xff151515.toInt(); p.textSize=20f; c.drawText(if(playing)"■" else "▶",w-155,43,p); p.color=0xff555555.toInt(); c.drawRoundRect(RectF(w-115,12,w-20,58),5f,5f,p); p.color=Color.WHITE; p.textSize=12f; c.drawText("TAP TEMPO",w-67,40,p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean { if(e.action!=MotionEvent.ACTION_DOWN)return true; val w=width.toFloat(); val h=height.toFloat(); if(e.y<70&&e.x>w-200){ playing=!playing; invalidate(); return true }
        val top=76f; val bottom=88f; val left=64f; val right=72f; val gap=4f; val tw=(w-left-right-gap*7)/8f; val sh=(h-top-bottom-gap*7)/8f
        if(e.x>w-right){ val s=((e.y-top)/(sh+gap)).toInt(); if(s in 0..7){ selected=s; for(i in active.indices) active[i]=true; invalidate() }; return true }
        val t=((e.x-left)/(tw+gap)).toInt(); val s=((e.y-top)/(sh+gap)).toInt(); if(t in 0..7 && s in 0..7){ selected=s; active[t]=!active[t]; invalidate(); return true }
        if(e.y>h-bottom){ if(t in 0..7){ armed[t]=!armed[t]; invalidate() } }; return true }
}
