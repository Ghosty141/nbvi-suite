
package org.netbeans.modules.jvi.impl;

import java.awt.Graphics;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;

import org.netbeans.editor.ext.ExtCaret;

import com.raelity.jvi.ViCaret;
import com.raelity.jvi.ViCaretStyle;
import com.raelity.jvi.core.G;
import com.raelity.jvi.manager.Scheduler;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.SwingPaintCaret;


/**
 * Extend the system's caret. Draw the
 * caret in different ways as defined by the cursor property.
 * // NEEDSWORK: cache the current font metric, listen to font property changes
 */
public class NbCaret extends ExtCaret implements ViCaret {
  // taken from MasterMatcher
  public static final String PROP_SEARCH_DIRECTION
                          = "nbeditor-bracesMatching-searchDirection"; //NOI18N
  public static final String D_BACKWARD = "backward-preferred"; //NOI18N
  public static final String D_FORWARD = "forward-preferred"; //NOI18N
  
  public static final String PROP_CARET_BIAS
                          = "nbeditor-bracesMatching-caretBias"; //NOI18N
  public static final String B_BACKWARD = "backward"; //NOI18N
  public static final String B_FORWARD = "forward"; //NOI18N
  
  public static final String PROP_MAX_BACKWARD_LOOKAHEAD
                    = "nbeditor-bracesMatching-maxBackwardLookahead"; //NOI18N
  public static final String PROP_MAX_FORWARD_LOOKAHEAD
                    = "nbeditor-bracesMatching-maxForwardLookahead"; //NOI18N
  
  SwingPaintCaret viDelegate;

  public NbCaret() {
    super();
    viDelegate = new com.raelity.jvi.swing.SwingPaintCaret(this);
    ViManager.setPlatformFindMatch(true);
    addChangeListener(new ChangeListener() {

        @Override
        public void stateChanged(ChangeEvent e)
        {
            Scheduler.cursorChange(NbCaret.this);
        }
    });
  }

  @Override
  public void setCursor(ViCaretStyle cursor) {
    viDelegate.setCursor(cursor);
    
    int offset = cursor.getMatchBraceOffset();
    if(G.p_pbm()) {
      if(offset == 0) { // command mode
        getTextComponent().putClientProperty(PROP_CARET_BIAS,
                B_FORWARD);
      } else {
        getTextComponent().putClientProperty(PROP_CARET_BIAS,
                B_BACKWARD);
      }
      getTextComponent().putClientProperty(PROP_SEARCH_DIRECTION,
              D_FORWARD);
      getTextComponent().putClientProperty(PROP_MAX_BACKWARD_LOOKAHEAD,
              new Integer(250));
      getTextComponent().putClientProperty(PROP_MAX_FORWARD_LOOKAHEAD,
              new Integer(250));

    } else {
      if(offset == 0) { // command mode
        getTextComponent().putClientProperty(PROP_CARET_BIAS,
                B_FORWARD);
      } else {
        getTextComponent().putClientProperty(PROP_CARET_BIAS,
                B_BACKWARD);
      }
      getTextComponent().putClientProperty(PROP_SEARCH_DIRECTION,
              D_FORWARD);
      getTextComponent().putClientProperty(PROP_MAX_BACKWARD_LOOKAHEAD,
              new Integer(0));
      getTextComponent().putClientProperty(PROP_MAX_FORWARD_LOOKAHEAD,
              new Integer(0));
    }
  }

  @Override
  public ViCaretStyle getCursor() {
    return viDelegate.getCursor();
  }

  private boolean paintCustomCaretCalled;
  // NetBeans only calls paintCustomCaret
  // if isVisible() is true and if its magic variable
  // blinkVisible is true. Whereas the swing caret sets isVisible
  // to false and calls paint. The default swing caret does nothing
  // if isVisible is false.
  // But jVi want's to paint different carets for not focus'd
  // and readonly. So if NB doesn't call custom paint, call it anyway.
  @Override
  public void paint(Graphics g)
  {
    paintCustomCaretCalled = false;
    super.paint(g);
    if(!paintCustomCaretCalled) {
      // guess the caret was not visible
      viDelegate.paint(g, false, dotChar, getTextComponent());
    }
  }

  /**
   * Render the caret as specified by the cursor.
   * <br>
   * Note: might want to check editor manager, and if not vi then
   * call super(paint), same for super(damage)
   */
    @Override
  protected void paintCustomCaret(Graphics g) {
    viDelegate.paint(g, isVisible(), dotChar, getTextComponent());
    paintCustomCaretCalled = true;
  }

    @Override
  public JTextComponent getTextComponent() {
    return component; // from super
  }
}

// vi:set sw=2 ts=8:
