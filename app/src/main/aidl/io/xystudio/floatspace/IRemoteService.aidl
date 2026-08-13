package io.xystudio.floatspace;

interface IRemoteService {
    int launchWindow(String component, int windowMode, int left, int top, int right, int bottom);
    int launchOnDisplay(String component, int displayId);
    boolean injectTouch(int displayId, int action, float x, float y, long downTime, long eventTime);
    int forceStop(String packageName);
    int enableWindowModes();
    int allowOverlay(String packageName);
    String runDiagnostic(String packageName);
    void destroy();
}
