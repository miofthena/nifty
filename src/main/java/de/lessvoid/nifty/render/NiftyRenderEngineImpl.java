package de.lessvoid.nifty.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;

import de.lessvoid.nifty.NiftyStopwatch;
import de.lessvoid.nifty.elements.render.TextRenderer.RenderFontNull;
import de.lessvoid.nifty.spi.render.RenderDevice;
import de.lessvoid.nifty.spi.render.RenderFont;
import de.lessvoid.nifty.spi.render.RenderImage;
import de.lessvoid.nifty.tools.Color;
import de.lessvoid.nifty.tools.ObjectPool;
import de.lessvoid.nifty.tools.ObjectPool.Factory;

/**
 * The Nifty RenderEngine.
 * @author void
 */
public class NiftyRenderEngineImpl implements NiftyRenderEngine {
  private Logger log = Logger.getLogger(NiftyRenderEngineImpl.class.getName());

  /**
   * RenderDevice.
   */
  private RenderDevice renderDevice;

  /**
   * Display width and height.
   */
  private int displayWidth;
  private int displayHeight;

  /**
   * global position x.
   */
  private float globalPosX = 0;

  /**
   * global position y.
   */
  private float globalPosY = 0;

  /**
   * current x position.
   */
  private float currentX = 0;

  /**
   * current y position.
   */
  private float currentY = 0;

  /**
   * font.
   */
  private RenderFont font;

  /**
   * current color.
   */
  private Color color = new Color(1.0f, 1.0f, 1.0f, 1.0f);

  /**
   * color changed.
   */
  private boolean colorChanged = false;

  /**
   * color alpha changed.
   */
  private boolean colorAlphaChanged = false;

  /**
   * current imageScale.
   */
  private float imageScale = 1.0f;

  /**
   * current textScale.
   */
  private float textScale = 1.0f;

  /**
   * font cache.
   */
  private Map < String, RenderFont > fontCache = new LinkedHashMap< String, RenderFont >();

  /**
   * stack to save data.
   */
  private Stack < SavedRenderState > stack = new Stack < SavedRenderState >();

  /**
   * ObjectPool to reuse SavedRenderState instances.
   */
  private ObjectPool<SavedRenderState> pool = new ObjectPool<SavedRenderState>(32, new Factory<SavedRenderState>() {
    @Override
    public SavedRenderState createNew() {
      return new SavedRenderState();
    }
  });

  private Clip clipEnabled = null;
  private BlendMode blendMode = BlendMode.BLEND;
  private NiftyImageManager imageManager;

  /**
   * create the device.
   * @param renderDeviceParam RenderDevice
   */
  public NiftyRenderEngineImpl(final RenderDevice renderDeviceParam) {
    renderDevice = renderDeviceParam;
    displayWidth = renderDevice.getWidth();
    displayHeight = renderDevice.getHeight();
    imageManager = new NiftyImageManager(renderDeviceParam);
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#getWidth()
   * @return width
   */
  public int getWidth() {
    return displayWidth;
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#getHeight()
   * @return height
   */
  public int getHeight() {
    return displayHeight;
  }

  public void beginFrame() {
    renderDevice.beginFrame();
    colorChanged = false;
  }

  public void endFrame() {
    renderDevice.endFrame();
  }

  public void clear() {
    renderDevice.clear();
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#createImage(java.lang.String, boolean)
   * @param filename name
   * @param filterLinear filter
   * @return NiftyImage
   */
  public NiftyImage createImage(final String filename, final boolean filterLinear) {
    if (filename == null) {
      return null;
    }
    return new NiftyImage(this, imageManager.getImage(filename, filterLinear));
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#createFont(java.lang.String)
   * @param filename name
   * @return RenderFont
   */
  public RenderFont createFont(final String filename) {
    if (filename == null) {
      return null;
    }
    if (fontCache.containsKey(filename)) {
      return fontCache.get(filename);
    } else {
      NiftyStopwatch.start();
      RenderFont newFont = renderDevice.createFont(filename);
      fontCache.put(filename, newFont);
      NiftyStopwatch.stop("RenderDevice.createFont(" + filename + ")");
      return newFont;
    }
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#renderQuad(int, int, int, int)
   * @param x x
   * @param y y
   * @param width width
   * @param height height
   */
  public void renderQuad(final int x, final int y, final int width, final int height) {
    renderDevice.renderQuad(x + getX(), y + getY(), width, height, color);
  }

  public void renderQuad(final int x, final int y, final int width, final int height, final Color topLeft, final Color topRight, final Color bottomRight, final Color bottomLeft) {
    if (isColorAlphaChanged()) {
      Color a = new Color(topLeft, color.getAlpha());
      Color b = new Color(topRight, color.getAlpha());
      Color c = new Color(bottomRight, color.getAlpha());
      Color d = new Color(bottomLeft, color.getAlpha());
      renderDevice.renderQuad(x + getX(), y + getY(), width, height, a, b, c, d);
    } else {
      renderDevice.renderQuad(x + getX(), y + getY(), width, height, topLeft, topRight, bottomRight, bottomLeft);
    }
  }

  /**
   * renderImage.
   * @param image image
   * @param x x
   * @param y y
   * @param width width
   * @param height height
   */
  public void renderImage(final NiftyImage image, final int x, final int y, final int width, final int height) {
    float alpha = 1.0f;
    if (color != null) {
      alpha = color.getAlpha();
    }
    image.render(x + getX(), y + getY(), width, height, new Color(1.0f, 1.0f, 1.0f, alpha), imageScale);
  }

  /**
   * renderText.
   * @param text text
   * @param x x
   * @param y y
   * @param selectionStart selection start
   * @param selectionEnd selection end
   * @param textSelectionColor textSelectionColor
   */
  public void renderText(
      final String text,
      final int x,
      final int y,
      final int selectionStart,
      final int selectionEnd,
      final Color textSelectionColor) {
    if (isSelection(selectionStart, selectionEnd)) {
      renderSelectionText(
          text, x + getX(), y + getY(), color, textSelectionColor, textScale, selectionStart, selectionEnd);
    } else {
      if (font == null || font instanceof RenderFontNull) {
        log.warning("missing font in renderText! could it be that you're using <text> elements without a font or style attribute? in case you've replaced <label> with <text> you're probably missing style='nifty-label' :)");
        return;
      }
      renderDevice.renderFont(font, text, x + getX(), y + getY(), color, textScale);
    }
  }

  /**
   * Render a Text with some text selected.
   * @param text text
   * @param x x
   * @param y y
   * @param textColor color
   * @param textSelectionColor textSelectionColor
   * @param textSize text size
   * @param selectionStartParam selection start
   * @param selectionEndParam selection end
   */
  protected void renderSelectionText(
      final String text,
      final int x,
      final int y,
      final Color textColor,
      final Color textSelectionColor,
      final float textSize,
      final int selectionStartParam,
      final int selectionEndParam) {
    int selectionStart = selectionStartParam;
    int selectionEnd = selectionEndParam;
    if (selectionStart < 0) {
      selectionStart = 0;
    }
    if (selectionEnd < 0) {
      selectionEnd = 0;
    }

    if (isEverythingSelected(text, selectionStart, selectionEnd)) {
      renderDevice.renderFont(font, text, x, y, textSelectionColor, textSize);
    } else if (isSelectionAtBeginning(selectionStart)) {
      String selectedString = text.substring(selectionStart, selectionEnd);
      String unselectedString = text.substring(selectionEnd);

      renderDevice.renderFont(font, selectedString, x, y, textSelectionColor, textSize);
      renderDevice.renderFont(font, unselectedString, x + font.getWidth(selectedString), y, textColor, textSize);
    } else if (isSelectionAtEnd(text, selectionEnd)) {
      String unselectedString = text.substring(0, selectionStart);
      String selectedString = text.substring(selectionStart, selectionEnd);

      renderDevice.renderFont(font, unselectedString, x, y, textColor, textSize);
      renderDevice.renderFont(font, selectedString, x + font.getWidth(unselectedString), y, textSelectionColor, textSize);
    } else {
      String unselectedString1 = text.substring(0, selectionStart);
      String selectedString = text.substring(selectionStart, selectionEnd);
      String unselectedString2 = text.substring(selectionEnd, text.length());

      renderDevice.renderFont(font, unselectedString1, x, y, textColor, textSize);
      int unselectedString1Len = font.getWidth(unselectedString1);
      renderDevice.renderFont(font, selectedString, x + unselectedString1Len, y, textSelectionColor, textSize);
      int selectedStringLen = font.getWidth(selectedString);
      renderDevice.renderFont(font, unselectedString2, x + unselectedString1Len + selectedStringLen, y, textColor, textSize);
    }
  }

  /**
   * Returns true of selection is at the end of the string.
   * @param text text
   * @param selectionEnd selection end
   * @return true or false
   */
  private boolean isSelectionAtEnd(final String text, final int selectionEnd) {
    return selectionEnd == text.length();
  }

  /**
   * Returns true if selection starts at the beginning.
   * @param selectionStart selection start
   * @return true or false
   */
  private boolean isSelectionAtBeginning(final int selectionStart) {
    return selectionStart == 0;
  }

  /**
   * Returns true when everything is selected.
   * @param text text
   * @param selectionStart selection start
   * @param selectionEnd selection end
   * @return true when everything is selected
   */
  private boolean isEverythingSelected(final String text, final int selectionStart, final int selectionEnd) {
    return isSelectionAtBeginning(selectionStart) && isSelectionAtEnd(text, selectionEnd);
  }

  /**
   * set font.
   * @param newFont font
   */
  public void setFont(final RenderFont newFont) {
    this.font = newFont;
  }

  /**
   * get font.
   * @return font
   */
  public RenderFont getFont() {
    return this.font;
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#setColor(de.lessvoid.nifty.tools.Color)
   * @param colorParam color
   */
  public void setColor(final Color colorParam) {
    color = new Color(colorParam);
    colorChanged = true;
    colorAlphaChanged = true;
  }

  /**
   * set only the color alpha.
   * @param newColorAlpha new alpha value
   */
  public void setColorAlpha(final float newColorAlpha) {
    color.setAlpha(newColorAlpha);
    colorAlphaChanged = true;
  }

  /**
   * Set only the color component of the given color. This assumes that alpha has already been changed.
   * @param newColor color
   */
  public void setColorIgnoreAlpha(final Color newColor) {
    color.setRed(newColor.getRed());
    color.setGreen(newColor.getGreen());
    color.setBlue(newColor.getBlue());
    colorChanged = true;

    if (colorAlphaChanged && color.getAlpha() > newColor.getAlpha()) {
      color.setAlpha(newColor.getAlpha());
      colorAlphaChanged = true;
    }
  }

  /**
   * return true when color has been changed.
   * @return color changed
   */
  public boolean isColorChanged() {
    return colorChanged;
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#isColorAlphaChanged()
   * @return color changed
   */
  public boolean isColorAlphaChanged() {
    return colorAlphaChanged;
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#moveTo(float, float)
   * @param xParam x
   * @param yParam y
   */
  public void moveTo(final float xParam, final float yParam) {
    this.currentX = xParam;
    this.currentY = yParam;
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#enableClip(int, int, int, int)
   * @param x0 x0
   * @param y0 y0
   * @param x1 x1
   * @param y1 y1
   */
  public void enableClip(final int x0, final int y0, final int x1, final int y1) {
    // in case there already is a clipping area set we can't override it. not sure if this is correct tho but it
    // fixes issues when some parent element has set a clipping area in which child elements should not be able to override the area.
    if (clipEnabled != null) {
      return;
    }
    updateClip(new Clip(x0 + getX(), y0 + getY(), x1 + getX(), y1 + getY()));
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#disableClip()
   */
  public void disableClip() {
    updateClip(null);
  }

  void updateClip(final Clip clip) {
    clipEnabled = clip;
    if (clipEnabled == null) {
      renderDevice.disableClip();
    } else {
      clipEnabled.apply();
    }
  }
  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#setRenderTextSize(float)
   * @param size size
   */
  public void setRenderTextSize(final float size) {
    this.textScale = size;
  }

  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#setImageScale(float)
   * @param scale scale
   */
  public void setImageScale(final float scale) {
    this.imageScale = scale;
  }
  /**
   * @see de.lessvoid.nifty.render.NiftyRenderEngine#setGlobalPosition(float, float)
   * @param xPos x
   * @param yPos y
   */
  public void setGlobalPosition(final float xPos, final float yPos) {
    globalPosX = xPos;
    globalPosY = yPos;
  }

  @Override
  public void displayResolutionChanged() {
    displayWidth = renderDevice.getWidth();
    displayHeight = renderDevice.getHeight();
  }

  /**
   * get x.
   * @return x
   */
  private int getX() {
    return (int) (globalPosX + currentX);
  }

  /**
   * get y.
   * @return y
   */
  private int getY() {
    return (int) (globalPosY + currentY);
  }

  /**
   * has selection.
   * @param selectionStart selection start
   * @param selectionEnd selection end
   * @return true or false
   */
  private boolean isSelection(final int selectionStart, final int selectionEnd) {
    return !(selectionStart == -1 && selectionEnd == -1);
  }

  public void saveState(final Set < RenderStateType > statesToSave) {
    SavedRenderState savedRenderState = pool.allocate();
    savedRenderState.save(statesToSave);
    stack.push(savedRenderState);
  }

  public void restoreState() {
    SavedRenderState restored = stack.pop();
    restored.restore();
    pool.free(restored);
  }

  public void setBlendMode(final BlendMode blendModeParam) {
    blendMode = blendModeParam;
    renderDevice.setBlendMode(blendModeParam);
  }

  public RenderDevice getRenderDevice() {
    return renderDevice;
  }

  public void disposeImage(final RenderImage image) {
    imageManager.dispose(image);
  }

  public RenderImage reload(final RenderImage image) {
    return imageManager.reload(image);
  }

  private class SavedRenderState {
    private float x;
    private float y;
    private boolean statePositionChanged;

    private float colorR;
    private float colorG;
    private float colorB;
    private boolean colorChanged;
    private boolean stateColorChanged;
    
    private float colorAlpha;
    private boolean colorAlphaChanged;
    private boolean stateAlphaChanged;
    
    private RenderFont font;
    private boolean stateFontChanged;
    
    private float textSize;
    private boolean stateTextSizeChanged;
    
    private float imageScale;
    private boolean stateImageScaleChanged;
    
    private Clip clipEnabled;
    private boolean stateClipChanged;
    
    private BlendMode blendMode;
    private boolean stateBlendModeChanged;

    private boolean restoreAll = false;

    public SavedRenderState() {
    }

    public void save(final Set<RenderStateType> statesToSave) {
      statePositionChanged = false;
      stateColorChanged = false;
      stateAlphaChanged = false;
      stateFontChanged = false;
      stateTextSizeChanged = false;
      stateImageScaleChanged = false;
      stateClipChanged = false;
      stateBlendModeChanged = false;
      restoreAll = false;

      if (statesToSave == null) {
        savePosition();
        saveColor();
        saveColorAlpha();
        saveTextSize();
        saveImageSize();
        saveFont();
        saveClipEnabled();
        saveBlendMode();
        restoreAll = true;
        return;
      }
      for (RenderStateType state : statesToSave) {
        if (RenderStateType.position.equals(state)) {
          savePosition();
        } else if (RenderStateType.color.equals(state)) {
          saveColor();
        } else if (RenderStateType.alpha.equals(state)) {
          saveColorAlpha();
        } else if (RenderStateType.textSize.equals(state)) {
          saveTextSize();
        } else if (RenderStateType.imageScale.equals(state)) {
          saveImageSize();
        } else if (RenderStateType.font.equals(state)) {
          saveFont();
        } else if (RenderStateType.clip.equals(state)) {
          saveClipEnabled();
        } else if (RenderStateType.blendMode.equals(state)) {
          saveBlendMode();
        }
      }
    }

    public void restore() {
      if (restoreAll) {
        restorePosition();
        restoreColor();
        restoreAlpha();
        restoreFont();
        restoreTextSize();
        restoreImageScale();
        restoreClip();
        restoreBlend();
        return;
      }
      if (statePositionChanged) {
        restorePosition();
      }
      if (stateColorChanged) {
        restoreColor();
      }
      if (stateAlphaChanged) {
        restoreAlpha();
      }
      if (stateFontChanged) {
        restoreFont();
      }
      if (stateTextSizeChanged) {
        restoreTextSize();
      }
      if (stateImageScaleChanged) {
        restoreImageScale();
      }
      if (stateClipChanged) {
        restoreClip();
      }
      if (stateBlendModeChanged) {
        restoreBlend();
      }
    }

    private void saveBlendMode() {
      blendMode = NiftyRenderEngineImpl.this.blendMode;
      stateBlendModeChanged = true;
    }

    private void saveClipEnabled() {
      clipEnabled = NiftyRenderEngineImpl.this.clipEnabled;
      stateClipChanged = true;
    }

    private void saveFont() {
      font = NiftyRenderEngineImpl.this.font;
      stateFontChanged = true;
    }

    private void saveImageSize() {
      imageScale = NiftyRenderEngineImpl.this.imageScale;
      stateImageScaleChanged = true;
    }

    private void saveTextSize() {
      textSize = NiftyRenderEngineImpl.this.textScale;
      stateTextSizeChanged = true;
    }

    private void saveColorAlpha() {
      colorAlpha = NiftyRenderEngineImpl.this.color.getAlpha();
      colorAlphaChanged = NiftyRenderEngineImpl.this.colorAlphaChanged;
      stateAlphaChanged = true;
    }

    private void saveColor() {
      colorR = NiftyRenderEngineImpl.this.color.getRed();
      colorG = NiftyRenderEngineImpl.this.color.getGreen();
      colorB = NiftyRenderEngineImpl.this.color.getBlue();
      colorChanged = NiftyRenderEngineImpl.this.colorChanged;
      stateColorChanged = true;
    }

    private void savePosition() {
      x = NiftyRenderEngineImpl.this.currentX;
      y = NiftyRenderEngineImpl.this.currentY;
      statePositionChanged = true;
    }

    private void restoreBlend() {
      NiftyRenderEngineImpl.this.setBlendMode(blendMode);
    }

    private void restoreClip() {
      NiftyRenderEngineImpl.this.updateClip(clipEnabled);
    }

    private void restoreImageScale() {
      NiftyRenderEngineImpl.this.imageScale = this.imageScale;
    }

    private void restoreTextSize() {
      NiftyRenderEngineImpl.this.textScale = this.textSize;
    }

    private void restoreFont() {
      NiftyRenderEngineImpl.this.font = font;
    }

    private void restoreAlpha() {
      NiftyRenderEngineImpl.this.color.setAlpha(colorAlpha);
      NiftyRenderEngineImpl.this.colorAlphaChanged = colorAlphaChanged;
    }

    private void restoreColor() {
      NiftyRenderEngineImpl.this.color.setRed(colorR);
      NiftyRenderEngineImpl.this.color.setGreen(colorG);
      NiftyRenderEngineImpl.this.color.setBlue(colorB);
      NiftyRenderEngineImpl.this.colorChanged = colorChanged;
    }

    private void restorePosition() {
      NiftyRenderEngineImpl.this.currentX = this.x;
      NiftyRenderEngineImpl.this.currentY = this.y;
    }
  }

  public class Clip {
    private int x0;
    private int y0;
    private int x1;
    private int y1;

    public Clip(final int x0, final int y0, final int x1, final int y1) {
      this.x0 = x0;
      this.y0 = y0;
      this.x1 = x1;
      this.y1 = y1;
    }

    public void apply() {
      renderDevice.enableClip(x0, y0, x1, y1);
    }
  }
}
