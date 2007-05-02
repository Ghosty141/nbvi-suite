
package org.netbeans.modules.jvi;

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


/**
 * Extend the system's caret. Draw the
 * caret in different ways as defined by the cursor property.
 * // NEEDSWORK: cache the current font metric, listen to font property changes
 */
public class NbCaret extends ExtCaret implements ViCaret {
  ViCaretDelegate viDelegate;
  static Method setMatchBraceOffset;

  public NbCaret() {
    super();
    viDelegate = new ViCaretDelegate(this);
    try {
      setMatchBraceOffset = getClass().getSuperclass()
                                .getMethod("setMatchBraceOffset", int.class);
    } catch (NoSuchMethodException ex) { }
  }
  
  public static boolean goodGotoMatchBehavior() {
      return setMatchBraceOffset != null;
  }

  public void setCursor(ViCursor cursor) {
    viDelegate.setCursor(cursor);
    
    // NEDSWORK: when development on NB6, can use a boolean to check
    //           if method is available and ifso invoke directly.
    
    //setMatchBraceOffset(cursor.getMatchBraceOffset());
    if(setMatchBraceOffset != null) {
      try {
        int offset = cursor.getMatchBraceOffset();
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

  // Following methods are intercepted to workaround the missing
  // positionCaret functionality.
  
    boolean mouseButtonDown;

    public void mousePressed(MouseEvent mouseEvent) {
        mouseButtonDown = true;
        beginClickHack(mouseEvent);
        super.mousePressed(mouseEvent);
        endClickHack();
    }
    
    public void mouseReleased(MouseEvent mouseEvent) {
        beginClickHack(mouseEvent);
        super.mouseReleased(mouseEvent);
        endClickHack();
        mouseButtonDown = false;
    }

    public void mouseClicked(MouseEvent mouseEvent) {
        beginClickHack(mouseEvent);
        super.mouseClicked(mouseEvent);
        endClickHack();
    }

    boolean isMouseAction = false;
    JTextComponent mouseComponent;
    public void setDot(int i) {
        if(isMouseAction || mouseButtonDown) {
            i = ViManager.mouseSetDot(i, mouseComponent);
        }
        super.setDot(i);
    }
    
    public void moveDot(int i) {
        if(mouseButtonDown)
            i = ViManager.mouseMoveDot(i, mouseComponent);
        super.moveDot(i);
    }
    
    private void beginClickHack(MouseEvent mouseEvent) {
        isMouseAction = true;
        mouseComponent = (JTextComponent)getEventComponent(mouseEvent);
    }
    
    private void endClickHack() {
        isMouseAction = false;
    }
}

