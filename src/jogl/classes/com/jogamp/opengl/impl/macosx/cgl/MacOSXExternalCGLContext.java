/*
 * Copyright (c) 2005 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that this software is not designed or intended for use
 * in the design, construction, operation or maintenance of any nuclear
 * facility.
 * 
 * Sun gratefully acknowledges that this software was originally authored
 * and developed by Kenneth Bradley Russell and Christopher John Kline.
 */

package com.jogamp.opengl.impl.macosx.cgl;

import javax.media.opengl.*;
import com.jogamp.opengl.impl.*;

import javax.media.nativewindow.*;
import com.jogamp.nativewindow.impl.NullWindow;

public class MacOSXExternalCGLContext extends MacOSXCGLContext {
  private boolean firstMakeCurrent = true;
  private GLContext lastContext;

  private MacOSXExternalCGLContext(Drawable drawable, boolean isNSContext, long handle) {
    super(drawable, null);
    drawable.setExternalCGLContext(this);
    this.isNSContext = isNSContext;
    this.contextHandle = handle;
    GLContextShareSet.contextCreated(this);
    setGLFunctionAvailability(false, 0, 0, CTX_PROFILE_COMPAT|CTX_OPTION_ANY);
    getGLStateTracker().setEnabled(false); // external context usage can't track state in Java
  }

  protected static MacOSXExternalCGLContext create(GLDrawableFactory factory, GLProfile glp) {
    long pixelFormat = 0;
    long currentDrawable = 0;
    long contextHandle = CGL.getCurrentContext(); // Check: MacOSX 10.3 ..
    boolean isNSContext = 0 != contextHandle;
    if( isNSContext ) {
        currentDrawable = CGL.getNSView(contextHandle);
        long ctx = CGL.getCGLContext(contextHandle);
        if (ctx == 0) {
          throw new GLException("Error: NULL Context (CGL) of Context (NS) 0x" +Long.toHexString(contextHandle));
        }
        pixelFormat = CGL.CGLGetPixelFormat(ctx);
        if(DEBUG) {
            System.err.println("MacOSXExternalCGLContext Create Context (NS) 0x"+Long.toHexString(contextHandle)+
                               ", Context (CGL) 0x"+Long.toHexString(ctx)+
                               ", pixelFormat 0x"+Long.toHexString(pixelFormat));
        }
    } else {
        contextHandle = CGL.CGLGetCurrentContext();
        if (contextHandle == 0) {
          throw new GLException("Error: current Context (CGL) null, no Context (NS)");
        }
        pixelFormat = CGL.CGLGetPixelFormat(contextHandle);
        if(DEBUG) {
            System.err.println("MacOSXExternalCGLContext Create Context (CGL) 0x"+Long.toHexString(contextHandle)+
                               ", pixelFormat 0x"+Long.toHexString(pixelFormat));
        }
    }

    if (0 == pixelFormat) {
      throw new GLException("Error: current pixelformat of current Context 0x"+Long.toHexString(contextHandle)+" is null");
    }
    GLCapabilities caps = MacOSXCGLGraphicsConfiguration.CGLPixelFormat2GLCapabilities(glp, pixelFormat);
    if(DEBUG) {
        System.err.println("MacOSXExternalCGLContext Create "+caps);
    }

    AbstractGraphicsScreen aScreen = DefaultGraphicsScreen.createDefault();
    MacOSXCGLGraphicsConfiguration cfg = new MacOSXCGLGraphicsConfiguration(aScreen, caps, caps, pixelFormat);

    NullWindow nw = new NullWindow(cfg);
    nw.setSurfaceHandle(currentDrawable); 
    return new MacOSXExternalCGLContext(new Drawable(factory, nw), isNSContext, contextHandle);
  }

  protected boolean createImpl() throws GLException {
      return true;
  }

  public int makeCurrent() throws GLException {
    // Save last context if necessary to allow external GLContexts to
    // talk to other GLContexts created by this library
    GLContext cur = getCurrent();
    if (cur != null && cur != this) {
      lastContext = cur;
      setCurrent(null);
    }
    return super.makeCurrent();
  }  

  public void release() throws GLException {
    super.release();
    setCurrent(lastContext);
    lastContext = null;
  }

  protected void makeCurrentImpl(boolean newCreated) throws GLException {
    if (firstMakeCurrent) {
      firstMakeCurrent = false;
    }
  }

  protected void releaseImpl() throws GLException {
  }

  protected void destroyImpl() throws GLException {
  }

  public void setOpenGLMode(int mode) {
    if (mode != MacOSXCGLDrawable.CGL_MODE)
      throw new GLException("OpenGL mode switching not supported for external GLContexts");
  }
    
  public int  getOpenGLMode() {
    return MacOSXCGLDrawable.CGL_MODE;
  }

  // Need to provide the display connection to extension querying APIs
  static class Drawable extends MacOSXCGLDrawable {
    MacOSXExternalCGLContext extCtx;

    Drawable(GLDrawableFactory factory, NativeWindow comp) {
      super(factory, comp, true);
    }

    void setExternalCGLContext(MacOSXExternalCGLContext externalContext) {
      extCtx = externalContext;
    }

    public GLContext createContext(GLContext shareWith) {
      throw new GLException("Should not call this");
    }

    public int getWidth() {
      throw new GLException("Should not call this");
    }

    public int getHeight() {
      throw new GLException("Should not call this");
    }

    public void setSize(int width, int height) {
      throw new GLException("Should not call this");
    }

    protected void swapBuffersImpl() {
      if (extCtx != null) {
        extCtx.swapBuffers();
      }
    }
  
    public void setOpenGLMode(int mode) {
        if (mode != CGL_MODE)
          throw new GLException("OpenGL mode switching not supported for external GLContext's drawables");
    }

    public int  getOpenGLMode() {
        return CGL_MODE;
    }
  }
}
