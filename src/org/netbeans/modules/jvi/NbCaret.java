
package org.netbeans.modules.jvi;

import com.raelity.jvi.core.G;
import com.raelity.jvi.ViCursor;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.swing.ViCaret;
import com.raelity.jvi.swing.SwingPaintCaret;
import java.awt.Graphics;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.ext.ExtCaret;


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

        public void stateChanged(ChangeEvent e)
        {
            ViManager.cursorChange(NbCaret.this);
        }
    });
  }

  public void setCursor(ViCursor cursor) {
    viDelegate.setCursor(cursor);
    
    int offset = cursor.getMatchBraceOffset();
    if(G.p_pbm.getBoolean()) {
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

  public ViCursor getCursor() {
    return viDelegate.getCursor();
  }

  /**
   * Render the caret as specified by the cursor.
   * <br>
   * Note: might want to check editor manager, and if not vi then
   * call super(paint), same for super(damage)
   */
    @Override
  protected void paintCustomCaret(Graphics g) {
    viDelegate.paint(g, getTextComponent());
  }

  public JTextComponent getTextComponent() {
    return component; // from super
  }
}

// vi:set sw=2 ts=8:
