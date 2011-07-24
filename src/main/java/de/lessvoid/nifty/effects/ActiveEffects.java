package de.lessvoid.nifty.effects;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This stores all active effects and is used by the EffectProcessor. It will
 * classify effects on add (overlay, pre, post).
 * @author void
 */
public class ActiveEffects {
  private Collection<Effect> all = new ArrayDeque<Effect>();
  private Collection<Effect> post = new ArrayDeque<Effect>();
  private Collection<Effect> pre = new ArrayDeque<Effect>();
  private Collection<Effect> overlay = new ArrayDeque<Effect>();

  private volatile boolean anyNotNeverStopRendering = true;

  public void clear() {
    all.clear();
    post.clear();
    pre.clear();
    overlay.clear();
    anyNotNeverStopRendering = _isAnyNotNeverStopRendering();
  }

  public void add(final Effect e) {
    all.add(e);
    anyNotNeverStopRendering = _isAnyNotNeverStopRendering();
    if (e.isOverlay()) {
      overlay.add(e);
    } else if (e.isPost()) {
      post.add(e);
    } else {
      pre.add(e);
    }
  }

  public void remove(final Effect e) {
    all.remove(e);
    anyNotNeverStopRendering = _isAnyNotNeverStopRendering();
    post.remove(e);
    pre.remove(e);
    overlay.remove(e);
  }

  public boolean isEmpty() {
    return all.isEmpty();
  }

  public boolean contains(final Effect e) {
    return all.contains(e);
  }

  public int size() {
    return all.size();
  }

  public boolean containsActiveEffects() {
    for (Effect e : all) {
        if (e.isActive()) {
            return true;
        }
    }
    return false;
  }

  public Collection<Effect> getActive() {
    return all;
  }

  public Collection<Effect> getActivePost() {
    return post;
  }

  public Collection<Effect> getActivePre() {
    return pre;
  }

  public Collection<Effect> getActiveOverlay() {
    return overlay;
  }

    public boolean isAnyNotNeverStopRendering() {
        return anyNotNeverStopRendering;
    }

    private boolean _isAnyNotNeverStopRendering(){
    for (Effect e : all) {
      if (e.isNeverStopRendering()) {
        return false;
      }
    }
    return true;
  }
}
