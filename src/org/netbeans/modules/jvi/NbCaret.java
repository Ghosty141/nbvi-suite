
package org.netbeans.modules.jvi;

import com.raelity.jvi.G;
import com.raelity.jvi.ViCursor;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.swing.ViCaret;
import com.raelity.jvi.swing.ViCaretDelegate;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.ext.ExtCaret;
import org.openide.util.Lookup;


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
  
  ViCaretDelegate viDelegate;
  static Method setMatchBraceOffset;
  static boolean matcher6;

  public NbCaret() {
    super();
    viDelegate = new com.raelity.jvi.swing.ViCaretDelegate(this);
    if(!goodGotoMatchBehavior()) {
      try {
        ((ClassLoader)(Lookup.getDefault().lookup(ClassLoader.class)))
            .loadClass("org.netbeans.spi.editor.bracesmatching.BracesMatcher");
        // java.lang.Class.forName("org.netbeans.spi.editor.bracesmatching.BracesMatcher");
        matcher6 = true;
        ViManager.setPlatformFindMatch(true);
      }catch(ClassNotFoundException ex) { }
      try {
        setMatchBraceOffset = getClass().getSuperclass()
                .getMethod("setMatchBraceOffset", int.class);
      } catch(java.lang.NoSuchMethodException ex) { }
    }
  }
  
  public static boolean goodGotoMatchBehavior() {
      return matcher6 || setMatchBraceOffset != null;
  }

  public void setCursor(ViCursor cursor) {
    viDelegate.setCursor(cursor);
    
    // NEDSWORK: when development on NB6, can use a boolean to check
    //           if method is available and ifso invoke directly.
    
    //setMatchBraceOffset(cursor.getMatchBraceOffset());
    int offset = cursor.getMatchBraceOffset();
    if(matcher6) {
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
    else if(setMatchBraceOffset != null) {
      try {
        if(offset == -1) {
          offset = java.lang.Integer.MAX_VALUE;
        }
        setMatchBraceOffset.invoke(this, offset);
      } catch (InvocationTargetException ex) {
      } catch (IllegalAccessException ex) {
      }
    }
  }

  public ViCursor getCursor() {
    return viDelegate.getCursor();
  }

  /*
  protected synchronized void damage(Rectangle r) {
    if(viDelegate.damage(this, r)) {
      repaint();
    }
  }
  
  protected void adjustVisibility(Rectangle nloc) {
    Rectangle r = new Rectangle();
    viDelegate.damage(r, nloc); // broaden to encompass whole character
    super.adjustVisibility(r);
  }
  */

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

  /**
   * Tries to set the position of the caret from
   * the coordinates of a mouse event, using viewToModel().
   * Notifies vi that the most has been clicked in window
   * and give vi a chance to adjust the position.
   *
   * @param e the mouse event
   */
  /* NEEDSWORK: positionCaret FUNCTIONALITY NOT AVAILABLE IN NETBEANS
  protected void positionCaret(MouseEvent e) {
    viDelegate.positionCaret(e);
  }
   */


    @Override
    public void setDot(int i) {
        if(isMouseAction || mouseButtonDown) {
            i = ViManager.mouseSetDot(i, mouseComponent, mouseEvent);
        }
        super.setDot(i);
    }
    
    @Override
    public void moveDot(int i) {
        if(mouseButtonDown)
            i = ViManager.mouseMoveDot(i, mouseComponent, mouseEvent);
        super.moveDot(i);
    }
  
    boolean mouseButtonDown;

    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        mouseButtonDown = true;
        beginClickHack(mouseEvent);
        super.mousePressed(mouseEvent);
        endClickHack();
    }
    
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        beginClickHack(mouseEvent);
        super.mouseReleased(mouseEvent);
        ViManager.mouseRelease(mouseEvent);
        endClickHack();
        mouseButtonDown = false;
    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
        beginClickHack(mouseEvent);
        super.mouseClicked(mouseEvent);
        endClickHack();
    }
    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        beginClickHack(mouseEvent);
        super.mouseDragged(mouseEvent);
        endClickHack();
    }

    boolean isMouseAction = false;
    JTextComponent mouseComponent;
    MouseEvent mouseEvent;
    
    private void beginClickHack(MouseEvent mouseEvent) {
        isMouseAction = true;
        this.mouseEvent = mouseEvent;
        mouseComponent = (JTextComponent)getEventComponent(mouseEvent);
    }
    
    private void endClickHack() {
        isMouseAction = false;
    }
}

// vi:set sw=2 ts=8:
