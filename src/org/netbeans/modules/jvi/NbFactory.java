package org.netbeans.modules.jvi;

import com.raelity.jvi.ViFS;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.Window;
import com.raelity.jvi.swing.CommandLine;
import com.raelity.jvi.swing.DefaultViFactory;
import com.raelity.jvi.swing.ViCaret;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JEditorPane;
import javax.swing.text.Caret;
import org.netbeans.modules.editor.NbEditorKit;
import org.openide.text.CloneableEditor;
import org.openide.windows.TopComponent;

public class NbFactory extends DefaultViFactory
{
    NbFS fs;
    
    public NbFactory() {
        super((CommandLine)null);
    }

    public ViFS getFS() {
        if(fs == null) {
            fs = new NbFS();
        }
        return new NbFS();
    }
    
    public Preferences getPreferences() {
        // return NbPreferences.forModule(ViManager.class); Not in 5.5
        Preferences retValue;
        
        retValue = super.getPreferences();
        return retValue;
    }

    ViTextView textView;
    public ViTextView getViTextView(JEditorPane editorPane) {

        // NEEDSWOWRK: probably need one text view per visible editor, cf JB.
	//             Note the textView references the status display.
	//             This might be "EditorUI".

	if(textView == null) {
	    textView = new NbTextView(editorPane);
	    textView.setWindow(new Window(textView));
	}
        return textView;
    }

    static boolean firstTime = true;
    public void registerEditorPane(JEditorPane editorPane) {
	if(firstTime) {
	    firstTime = false;
	    startupNbVi();
	}
      if(true) {
	super.registerEditorPane(editorPane);
      } else {
	// install cursor if neeeded
	Caret c = editorPane.getCaret();
	if( ! (c instanceof ViCaret)) {
	    NbCaret caret = new NbCaret();
	    editorPane.setCaret(caret);
	    caret.setDot(c.getDot());
	    caret.setBlinkRate(c.getBlinkRate());
	  // if(attachTrace) { System.err.println("registerEditorPane caret: " + editorPane);}
	}
      }
    }

    /*
    // From Window System API
    TopComponent.Registry.addPropertyChangeListener(PropertyChangeListener)
    WindowManager.getModes()
      or WindowManager.findMode(String)
      or WindowManager.findMode(TopComponent)
    WindowManager.getDefault()
    TopComponent.requestActive()
    TopComponent.close()
    */
    /**
     * Convert a TopComponent we're willing to deal with 
     * to a convenient file object.
     */
    private static Object getViEditorThing(Object o) {
        if(o instanceof CloneableEditor) {
            JEditorPane ep = ((CloneableEditor)o).getEditorPane();
            // NEEDSWORK: should we be more specific
            //            about what is accpted?
            //            Could tag our editors with an interface
            if(ep.getEditorKit() instanceof NbEditorKit) {
                return o;
            }
        }
        return null;
    }
    
    private static void startupNbVi() {
        
        // Monitor activations/opens/closes.
        // NEEDSWORK: in NB6.0 may be able to monitor WindowManager Mode.
        //            something like WindowManager.findMode("editor").addPropertyChangeListener
        TopComponent.getRegistry().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println("NbVi REG evt = " + evt.getPropertyName() + ": "
                        + evt.getOldValue()
                        + " --> " + evt.getNewValue());
                if(evt.getPropertyName().equals(TopComponent.Registry.PROP_ACTIVATED)) {
                    Object thing = getViEditorThing(evt.getOldValue());
                    if(thing != null) {
                        // Do this so don't hold begin/endUndo
                        ViManager.exitInputMode();
                    }
                    thing = getViEditorThing(evt.getNewValue());
                    if(thing != null) {
                        ViManager.activateFile(null /*browser*/, thing);
                    }
                } else if(evt.getPropertyName().equals(TopComponent.Registry.PROP_OPENED)) {
                    // For each top component we know about, see if it is still
                    // opened.
                    // NEEDSWORK: checking each buffer, this seems wasteful
                    // NEEDSWORK: use the org.netbeans.editor.Registry ??
                    Set<TopComponent> s = (Set<TopComponent>)evt.getNewValue();
                    for(int i = 1; ; i++) {
                        Object o = ViManager.getTextBuffer(i);
                        if(o == null) {
                            break;
                        }
                        if(!s.contains(o)) {
                            ViManager.deactivateFile(null /*browser*/, o);
			    JEditorPane ep = ((CloneableEditor)o).getEditorPane();
			    ViTextView tv = ViManager.getViFactory().getViTextView(ep);
			    tv.detach();
                        }
                    }
                }
            }
        });
    }

}
