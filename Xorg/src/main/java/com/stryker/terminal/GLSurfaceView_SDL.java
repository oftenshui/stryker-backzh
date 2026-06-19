/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stryker.terminal;

import android.app.KeyguardManager;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import javax.microedition.khronos.egl.*;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;
import java.io.Writer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

@SuppressWarnings("ALL")
public class GLSurfaceView_SDL extends SurfaceView implements SurfaceHolder.Callback {
  public final static int RENDERMODE_WHEN_DIRTY = 0;
  public final static int RENDERMODE_CONTINUOUSLY = 1;

  public final static int DEBUG_CHECK_GL_ERROR = 1;

  public final static int DEBUG_LOG_GL_CALLS = 2;

  public GLSurfaceView_SDL(Context context) {
    super(context);
    init();
  }

  public GLSurfaceView_SDL(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    SurfaceHolder holder = getHolder();
    holder.addCallback(this);
    holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
    mKeyguardManager = ((KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE));
  }

  public void setGLWrapper(GLWrapper glWrapper) {
    mGLWrapper = glWrapper;
  }

  public void setDebugFlags(int debugFlags) {
    mDebugFlags = debugFlags;
  }

  public int getDebugFlags() {
    return mDebugFlags;
  }

  public void setRenderer(Renderer renderer) {
    if (mGLThread != null) {
      throw new IllegalStateException(
        "setRenderer has already been called for this instance.");
    }
    if (mEGLConfigChooser == null) {
      mEGLConfigChooser = getEglConfigChooser(16, false, false, false, false);
    }
    mGLThread = new GLThread(renderer);
    mGLThread.start();
  }

  public void setEGLConfigChooser(EGLConfigChooser configChooser) {
    if (mGLThread != null) {
      throw new IllegalStateException(
        "setRenderer has already been called for this instance.");
    }
    mEGLConfigChooser = configChooser;
  }

  public void setEGLConfigChooser(int bpp, boolean needDepth, boolean stencil, boolean gles2, boolean gles3) {
    setEGLConfigChooser(getEglConfigChooser(bpp, needDepth, stencil, gles2, gles3));
  }

  public void setEGLConfigChooser(int redSize, int greenSize, int blueSize,
                                  int alphaSize, int depthSize, int stencilSize, boolean gles2, boolean gles3) {
    setEGLConfigChooser(new ComponentSizeChooser(redSize, greenSize,
      blueSize, alphaSize, depthSize, stencilSize, gles2, gles3));
  }

  public void setRenderMode(int renderMode) {
    mGLThread.setRenderMode(renderMode);
  }

  public int getRenderMode() {
    return mGLThread.getRenderMode();
  }

  public void requestRender() {
    mGLThread.requestRender();
  }

  public void surfaceCreated(SurfaceHolder holder) {
    mGLThread.surfaceCreated();
  }

  public void surfaceDestroyed(SurfaceHolder holder) {
    mGLThread.surfaceDestroyed();
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    mGLThread.onWindowResize(w, h);
  }

  public void onPause() {
    mGLThread.onPause();
  }

  public void onResume() {
    mGLThread.onResume();
  }

  public void queueEvent(Runnable r) {
    mGLThread.queueEvent(r);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    mGLThread.requestExitAndWait();
  }

  public interface GLWrapper {
    GL wrap(GL gl);
  }

  public static interface SwapBuffersCallback {
    public boolean SwapBuffers();

    public void ResetVideoSurface();

    public void onWindowResize(int width, int height);
  }

  public static abstract class Renderer {
    public abstract void onSurfaceCreated(GL10 gl, EGLConfig config);

    public abstract void onSurfaceDestroyed();

    public abstract void onSurfaceChanged(GL10 gl, int width, int height);

    public void onWindowResize(int width, int height) {
      if (mSwapBuffersCallback != null)
        mSwapBuffersCallback.onWindowResize(width, height);
    }

    public abstract void onDrawFrame(GL10 gl);

    public boolean SwapBuffers() {
      if (mSwapBuffersCallback != null)
        return mSwapBuffersCallback.SwapBuffers();
      return false;
    }

    public void ResetVideoSurface() {
      if (mSwapBuffersCallback != null)
        mSwapBuffersCallback.ResetVideoSurface();
    }

    public void setSwapBuffersCallback(SwapBuffersCallback c) {
      mSwapBuffersCallback = c;
    }

    private SwapBuffersCallback mSwapBuffersCallback = null;
  }

  public interface EGLConfigChooser {
    EGLConfig chooseConfig(EGL10 egl, EGLDisplay display);

    public boolean isGles2Required();

    public boolean isGles3Required();
  }

  private static abstract class BaseConfigChooser
    implements EGLConfigChooser {
    public BaseConfigChooser(int[] configSpec) {
      mConfigSpec = configSpec;
    }

    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
      int[] num_config = new int[1];
      egl.eglChooseConfig(display, mConfigSpec, null, 0, num_config);

      int numConfigs = num_config[0];

      if (numConfigs <= 0) {
        throw new IllegalArgumentException(
          "No configs match configSpec");
      }

      EGLConfig[] configs = new EGLConfig[numConfigs];
      egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs,
        num_config);
      EGLConfig config = chooseConfig(egl, display, configs);
      if (config == null) {
        throw new IllegalArgumentException("No config chosen");
      }
      return config;
    }

    abstract EGLConfig chooseConfig(EGL10 egl, EGLDisplay display,
                                    EGLConfig[] configs);

    protected int[] mConfigSpec;
  }

  private static class ComponentSizeChooser extends BaseConfigChooser {
    public ComponentSizeChooser(int redSize, int greenSize, int blueSize,
                                int alphaSize, int depthSize, int stencilSize, boolean isGles2, boolean isGles3) {
      super(new int[]{EGL10.EGL_NONE});
      mValue = new int[1];
      mRedSize = redSize;
      mGreenSize = greenSize;
      mBlueSize = blueSize;
      mAlphaSize = alphaSize;
      mDepthSize = depthSize;
      mStencilSize = stencilSize;
      mIsGles2 = isGles2;
      mIsGles3 = isGles3;
    }

    @Override
    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display,
                                  EGLConfig[] configs) {
      EGLConfig closestConfig = null;
      int closestDistance = 1000;
      String cfglog = "";
      int idx = 0;
      int selectidx = -1;

      Log.v("SDL", "Desired GL config: " + "R" + mRedSize + "G" + mGreenSize + "B" + mBlueSize + "A" + mAlphaSize + " depth " + mDepthSize + " stencil " + mStencilSize + " type " + (mIsGles3 ? "GLES3" : mIsGles2 ? "GLES2" : "GLES"));
      for (EGLConfig config : configs) {
        if (config == null)
          continue;
        int r = findConfigAttrib(egl, display, config,
          EGL10.EGL_RED_SIZE, 0);
        int g = findConfigAttrib(egl, display, config,
          EGL10.EGL_GREEN_SIZE, 0);
        int b = findConfigAttrib(egl, display, config,
          EGL10.EGL_BLUE_SIZE, 0);
        int a = findConfigAttrib(egl, display, config,
          EGL10.EGL_ALPHA_SIZE, 0);
        int d = findConfigAttrib(egl, display, config,
          EGL10.EGL_DEPTH_SIZE, 0);
        int s = findConfigAttrib(egl, display, config,
          EGL10.EGL_STENCIL_SIZE, 0);
        int rendertype = findConfigAttrib(egl, display, config,
          EGL10.EGL_RENDERABLE_TYPE, 0);
        int desiredtype = mIsGles3 ? EGL_OPENGL_ES3_BIT : mIsGles2 ? EGL_OPENGL_ES2_BIT : EGL_OPENGL_ES_BIT;
        int nativeRender = findConfigAttrib(egl, display, config,
          EGL10.EGL_NATIVE_RENDERABLE, 0);
        int caveat = findConfigAttrib(egl, display, config,
          EGL10.EGL_CONFIG_CAVEAT, EGL10.EGL_NONE);
        int distance = Math.abs(r - mRedSize) + Math.abs(g - mGreenSize) + Math.abs(b - mBlueSize);
        int dist1 = distance;
        if (mAlphaSize - a > 0)
          distance += mAlphaSize - a;
        else if (mAlphaSize - a < 0)
          distance += 1;
        int dist2 = distance;
        if ((d > 0) != (mDepthSize > 0))
          distance += (mDepthSize > 0) ? 5 : 1;
        int dist3 = distance;
        if ((s > 0) != (mStencilSize > 0))
          distance += (mStencilSize > 0) ? 5 : 1;
        int dist4 = distance;
        if ((rendertype & desiredtype) == 0)
          distance += 5;
        int dist5 = distance;
        if (caveat == EGL10.EGL_SLOW_CONFIG)
          distance += 4;
        if (caveat == EGL10.EGL_NON_CONFORMANT_CONFIG)
          distance += 1;

        String cfgcur = "R" + r + "G" + g + "B" + b + "A" + a + " depth " + d + " stencil " + s +
          " type " + rendertype + " (";
        if ((rendertype & EGL_OPENGL_ES_BIT) != 0)
          cfgcur += "GLES";
        if ((rendertype & EGL_OPENGL_ES2_BIT) != 0)
          cfgcur += " GLES2";
        if ((rendertype & EGL_OPENGL_ES3_BIT) != 0)
          cfgcur += " GLES3";
        if ((rendertype & EGL_OPENGL_BIT) != 0)
          cfgcur += " OPENGL";
        if ((rendertype & EGL_OPENVG_BIT) != 0)
          cfgcur += " OPENVG";
        cfgcur += ")";
        cfgcur += " caveat " + (caveat == EGL10.EGL_NONE ? "none" :
          (caveat == EGL10.EGL_SLOW_CONFIG ? "SLOW" :
            caveat == EGL10.EGL_NON_CONFORMANT_CONFIG ? "non-conformant" :
              String.valueOf(caveat)));
        cfgcur += " nr " + nativeRender;
        cfgcur += " pos " + distance + " (" + dist1 + "," + dist2 + "," + dist3 + "," + dist4 + "," + dist5 + ")";
        Log.v("SDL", "GL config " + idx + ": " + cfgcur);
        if (distance < closestDistance) {
          closestDistance = distance;
          closestConfig = config;
          cfglog = new String(cfgcur);
          selectidx = idx;
        }
        idx += 1;
      }
      Log.v("SDL", "GLSurfaceView_SDL::EGLConfigChooser::chooseConfig(): selected " + selectidx + ": " + cfglog);
      return closestConfig;
    }

    private int findConfigAttrib(EGL10 egl, EGLDisplay display,
                                 EGLConfig config, int attribute, int defaultValue) {
      mValue[0] = -1;
      if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
        return mValue[0];
      }
      Log.w("SDL", "GLSurfaceView_SDL::EGLConfigChooser::findConfigAttrib(): attribute doesn't exist: " + attribute);
      return defaultValue;
    }

    public boolean isGles2Required() {
      return mIsGles2;
    }

    public boolean isGles3Required() {
      return mIsGles3;
    }

    private int[] mValue;
    protected int mRedSize;
    protected int mGreenSize;
    protected int mBlueSize;
    protected int mAlphaSize;
    protected int mDepthSize;
    protected int mStencilSize;
    protected boolean mIsGles2 = false;
    protected boolean mIsGles3 = false;

    public static final int EGL_OPENGL_ES_BIT = 1;
    public static final int EGL_OPENVG_BIT = 2;
    public static final int EGL_OPENGL_ES2_BIT = 4;
    public static final int EGL_OPENGL_BIT = 8;
    public static final int EGL_OPENGL_ES3_BIT = 16;
  }

  private static class SimpleEGLConfigChooser16 extends ComponentSizeChooser {
    public SimpleEGLConfigChooser16(boolean withDepthBuffer, boolean stencil, boolean gles2, boolean gles3) {
      super(4, 4, 4, 0, withDepthBuffer ? 16 : 0, stencil ? 8 : 0, gles2, gles3);
      mRedSize = 5;
      mGreenSize = 6;
      mBlueSize = 5;
    }
  }

  private static class SimpleEGLConfigChooser24 extends ComponentSizeChooser {
    public SimpleEGLConfigChooser24(boolean withDepthBuffer, boolean stencil, boolean gles2, boolean gles3) {
      super(8, 8, 8, 0, withDepthBuffer ? 16 : 0, stencil ? 8 : 0, gles2, gles3);
      mRedSize = 8;
      mGreenSize = 8;
      mBlueSize = 8;
    }
  }

  private static class SimpleEGLConfigChooser32 extends ComponentSizeChooser {
    public SimpleEGLConfigChooser32(boolean withDepthBuffer, boolean stencil, boolean gles2, boolean gles3) {
      super(8, 8, 8, 8, withDepthBuffer ? 16 : 0, stencil ? 8 : 0, gles2, gles3);
      mRedSize = 8;
      mGreenSize = 8;
      mBlueSize = 8;
      mAlphaSize = 8;
    }
  }

  private static ComponentSizeChooser getEglConfigChooser(int videoDepthBpp, boolean withDepthBuffer, boolean stencil, boolean gles2, boolean gles3) {
    if (videoDepthBpp == 16)
      return new SimpleEGLConfigChooser16(withDepthBuffer, stencil, gles2, gles3);
    if (videoDepthBpp == 24)
      return new SimpleEGLConfigChooser24(withDepthBuffer, stencil, gles2, gles3);
    if (videoDepthBpp == 32)
      return new SimpleEGLConfigChooser32(withDepthBuffer, stencil, gles2, gles3);
    return null;
  }

  ;

  private class EglHelper {
    public EglHelper() {

    }

    public void start() {

      Log.v("SDL", "GLSurfaceView_SDL::EglHelper::start(): creating GL context");
      mEgl = (EGL10) EGLContext.getEGL();

      mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

      int[] version = new int[2];
      mEgl.eglInitialize(mEglDisplay, version);
      mEglConfig = mEGLConfigChooser.chooseConfig(mEgl, mEglDisplay);
      if (mEglConfig == null)
        Log.e("SDL", "GLSurfaceView_SDL::EglHelper::start(): mEglConfig is NULL");

      final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
      final int[] gles2_attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
      final int[] gles3_attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE};

      mEglContext = mEgl.eglCreateContext(mEglDisplay, mEglConfig,
        EGL10.EGL_NO_CONTEXT,
        mEGLConfigChooser.isGles3Required() ? gles3_attrib_list :
          mEGLConfigChooser.isGles2Required() ? gles2_attrib_list : null);

      if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT)
        Log.e("SDL", "GLSurfaceView_SDL::EglHelper::start(): mEglContext is EGL_NO_CONTEXT, error: " + mEgl.eglGetError());

      mEglSurface = null;
    }

    public GL createSurface(SurfaceHolder holder) {
      Log.v("SDL", "GLSurfaceView_SDL::EglHelper::createSurface(): creating GL context");
      if (mEglSurface != null) {

        mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE,
          EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
      }

      mEglSurface = mEgl.eglCreateWindowSurface(mEglDisplay,
        mEglConfig, holder, null);

      mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface,
        mEglContext);

      GL gl = mEglContext.getGL();
      if (mGLWrapper != null) {
        gl = mGLWrapper.wrap(gl);
      }

      return gl;
    }

    public boolean swap() {
      mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);

      return mEgl.eglGetError() != EGL11.EGL_CONTEXT_LOST;
    }

    public void finish() {
      Log.v("SDL", "GLSurfaceView_SDL::EglHelper::finish(): destroying GL context");
      if (mEglSurface != null) {
        mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE,
          EGL10.EGL_NO_SURFACE,
          EGL10.EGL_NO_CONTEXT);
        mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
        mEglSurface = null;
      }
      if (mEglContext != null) {
        mEgl.eglDestroyContext(mEglDisplay, mEglContext);
        mEglContext = null;
      }
      if (mEglDisplay != null) {
        mEgl.eglTerminate(mEglDisplay);
        mEglDisplay = null;
      }
    }

    EGL10 mEgl;
    EGLDisplay mEglDisplay;
    EGLSurface mEglSurface;
    EGLConfig mEglConfig;
    EGLContext mEglContext;
  }

  class GLThread extends Thread implements SwapBuffersCallback {
    GLThread(Renderer renderer) {
      super();
      mDone = false;
      mWidth = 0;
      mHeight = 0;
      mRequestRender = true;
      mRenderMode = RENDERMODE_CONTINUOUSLY;
      mRenderer = renderer;
      mRenderer.setSwapBuffersCallback(this);
      setName("GLThread");
    }

    @Override
    public void run() {
      try {
        sEglSemaphore.acquire();
      } catch (InterruptedException e) {
        return;
      }

      mEglHelper = new EglHelper();
      mNeedStart = true;
      mSizeChanged = true;
      SwapBuffers();

      mRenderer.onDrawFrame(mGL);

      mEglHelper.finish();

      sEglSemaphore.release();
    }

    public void ResetVideoSurface() {
      mResetVideoSurface = true;
    }

    public boolean SwapBuffers() {

      boolean tellRendererSurfaceCreated = false;
      boolean tellRendererSurfaceChanged = false;

      while (true) {

        int w, h;
        boolean changed = false;
        synchronized (this) {
          if (mPaused) {
            mRenderer.onSurfaceDestroyed();
            mEglHelper.finish();
            mNeedStart = true;
            if (Globals.NonBlockingSwapBuffers)
              return false;
          }
        }
        while (needToWait()) {
          synchronized (this) {
            try {
              wait(500);
            } catch (InterruptedException e) {
              Log.v("SDL", "GLSurfaceView_SDL::GLThread::SwapBuffers(): Who dared to interrupt my slumber?");
              Thread.interrupted();
            }
          }
        }
        synchronized (this) {
          if (mDone)
            return false;
          w = mWidth;
          h = mHeight;
          mSizeChanged = false;
          mRequestRender = false;
        }
        if (mNeedStart) {
          mEglHelper.start();
          tellRendererSurfaceCreated = true;
          changed = true;
          mNeedStart = false;
        }
        if (changed) {
          mGL = (GL10) mEglHelper.createSurface(getHolder());
          tellRendererSurfaceChanged = true;
        }
        if (tellRendererSurfaceCreated) {
          mRenderer.onSurfaceCreated(mGL, mEglHelper.mEglConfig);
          tellRendererSurfaceCreated = false;
        }
        if (tellRendererSurfaceChanged) {
          mRenderer.onSurfaceChanged(mGL, w, h);
          tellRendererSurfaceChanged = false;
        }
        if (!mResetVideoSurface && mEglHelper.swap())
          return true;
        mResetVideoSurface = false;
        mRenderer.onSurfaceDestroyed();
        mEglHelper.finish();
        mNeedStart = true;
        if (Globals.NonBlockingSwapBuffers)
          return false;
      }
    }

    private boolean needToWait() {
      if (mKeyguardManager.inKeyguardRestrictedInputMode()) {
        return true;
      }

      synchronized (this) {
        if (mDone) {
          return false;
        }

        if (Globals.HorizontalOrientation != (mWidth > mHeight))
          return true;

        if (mPaused || (!mHasSurface)) {
          return true;
        }

        if ((mWidth > 0) && (mHeight > 0) && (mRequestRender || (mRenderMode == RENDERMODE_CONTINUOUSLY))) {
          return false;
        }
      }

      return true;
    }

    public void setRenderMode(int renderMode) {
      if (!((RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= RENDERMODE_CONTINUOUSLY))) {
        throw new IllegalArgumentException("renderMode");
      }
      synchronized (this) {
        mRenderMode = renderMode;
        if (renderMode == RENDERMODE_CONTINUOUSLY) {
          notify();
        }
      }
    }

    public int getRenderMode() {
      synchronized (this) {
        return mRenderMode;
      }
    }

    public void requestRender() {
      synchronized (this) {
        mRequestRender = true;
        notify();
      }
    }

    public void surfaceCreated() {
      synchronized (this) {
        mHasSurface = true;
        notify();
      }
    }

    public void surfaceDestroyed() {
      synchronized (this) {
        mHasSurface = false;
        notify();
      }
    }

    public void onPause() {
      Log.v("SDL", "GLSurfaceView_SDL::onPause()");
      synchronized (this) {
        mPaused = true;
      }
    }

    public void onResume() {
      Log.v("SDL", "GLSurfaceView_SDL::onResume()");
      synchronized (this) {
        mPaused = false;
        notify();
      }
    }

    public void onWindowResize(int w, int h) {
      Log.v("SDL", "GLSurfaceView_SDL::onWindowResize(): " + w + "x" + h);
      synchronized (this) {
        mWidth = w;
        mHeight = h;
        mSizeChanged = true;
        mRenderer.onWindowResize(w, h);
        notify();
      }
    }

    public void requestExitAndWait() {
      Log.v("SDL", "GLSurfaceView_SDL::requestExitAndWait()");
      synchronized (this) {
        mDone = true;
        notify();
      }
      try {
        join();
      } catch (InterruptedException ex) {
      }
    }

    public void queueEvent(Runnable r) {
      synchronized (this) {
        mEventQueue.add(r);
      }
    }

    private Runnable getEvent() {
      synchronized (this) {
        if (mEventQueue.size() > 0) {
          return mEventQueue.remove(0);
        }

      }
      return null;
    }

    private boolean mDone;
    private boolean mPaused;
    private boolean mHasSurface;
    private int mWidth;
    private int mHeight;
    private int mRenderMode;
    private boolean mRequestRender;
    private Renderer mRenderer;
    private ArrayList<Runnable> mEventQueue = new ArrayList<Runnable>();
    private EglHelper mEglHelper;
    private GL10 mGL = null;
    private boolean mNeedStart = false;
    private boolean mResetVideoSurface = false;
  }

  static class LogWriter extends Writer {

    @Override public void close() {
      flushBuilder();
    }

    @Override public void flush() {
      flushBuilder();
    }

    @Override public void write(char[] buf, int offset, int count) {
      for (int i = 0; i < count; i++) {
        char c = buf[offset + i];
        if (c == '\n') {
          flushBuilder();
        } else {
          mBuilder.append(c);
        }
      }
    }

    private void flushBuilder() {
      if (mBuilder.length() > 0) {
        Log.v("GLSurfaceView", mBuilder.toString());
        mBuilder.delete(0, mBuilder.length());
      }
    }

    private StringBuilder mBuilder = new StringBuilder();
  }

  private static final Semaphore sEglSemaphore = new Semaphore(1);
  private boolean mSizeChanged = true;

  private GLThread mGLThread;
  private EGLConfigChooser mEGLConfigChooser;
  private GLWrapper mGLWrapper;
  private int mDebugFlags;
  private KeyguardManager mKeyguardManager;
}
