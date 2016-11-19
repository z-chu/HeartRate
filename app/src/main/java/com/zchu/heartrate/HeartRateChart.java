package com.zchu.heartrate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;

/**
 * 作者: zchu on 2016/9/13 0013.
 */
public class HeartRateChart extends View {
    private int mStrokeColor = Color.RED;
    private float mStrokeWidth = 8;
    private float pathSpace = 10f; //每个路径点的间距
    private long maxX; //x坐标能在屏幕中看的的最大值
    private Paint mPaint;
    private LinkedList<Float> linkedPathList;
    private float minPathY=0; //y轴最小的点


    public HeartRateChart(Context context) {
        super(context);
        init();
    }

    public HeartRateChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    private void init() {
        linkedPathList = new LinkedList<>();
        maxX = getContext().getResources().getDisplayMetrics().widthPixels;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(mStrokeColor);
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setPathEffect(new CornerPathEffect(60));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (linkedPathList.isEmpty()) {
            return;
        }
        Path path = new Path();
        int i = 0;
        for (Float aFloat : linkedPathList) {
            if (i == 0) {
                path.moveTo(i * pathSpace, aFloat);
            }
            path.lineTo(i * pathSpace, aFloat);
            i++;
        }
        canvas.drawPath(path, mPaint);


    }

    //通过不断调用该方法，将传入的参数绘制成一条线
    public void lineTo(float y) {
        if(minPathY>0) {//当minPathY被赋值时开始线条起伏增幅
            linkedPathList.add(y * 25 - minPathY*24); //根据线条幅度增大
        }else{
            linkedPathList.add(y);
        }
        if (linkedPathList.size() * pathSpace > maxX) {//如果线条总长大于屏幕宽度了

            if(minPathY==0){
                for (Float aFloat : linkedPathList) {
                    if(minPathY==0||minPathY>aFloat){
                        minPathY=aFloat; //取出链表中Y坐标最小的点，赋值给minPathY
                    }
                }
            }
            linkedPathList.removeFirst();//删掉最右边的点
        }
        invalidate();
    }

    public void clear() {
        linkedPathList.clear();
        minPathY=0;
    }

}
