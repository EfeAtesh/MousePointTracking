package com.efeates.mpointtracking;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;

import java.util.ArrayList;

public class MyTrackerAccessibilityService extends AccessibilityService {

    public static boolean isRecording = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isRecording) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo node = event.getSource();
            if (node != null) {
                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                
                float x = rect.exactCenterX();
                float y = rect.exactCenterY();
                
                Log.d("TRACKER", "Background click detected on " + event.getPackageName() + " at approximately x=" + x + ", y=" + y);
                
                ArrayList<Float> point = new ArrayList<>();
                point.add(x);
                point.add(y);
                
                Main.points.add(point);
                
                node.recycle();
            } else {
                 Log.d("TRACKER", "Background click detected (no view info)");
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED;
        
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;

        setServiceInfo(info);
    }
}
