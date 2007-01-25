package org.netbeans.modules.jvi;

import com.raelity.jvi.G;
import com.raelity.jvi.ViFS;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.Window;
import com.raelity.jvi.swing.CommandLine;
import com.raelity.jvi.swing.DefaultViFactory;
import com.raelity.jvi.swing.KeyBinding;
import com.raelity.jvi.swing.ViCaret;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Caret;
import javax.swing.text.TextAction;
import org.netbeans.editor.Registry;
import org.netbeans.modules.editor.NbEditorKit;
import org.openide.text.CloneableEditor;
import org.openide.windows.TopComponent;

public class NbFactory extends DefaultViFactory {
    NbFS fs = new NbFS();
    
    // NEEDSWOWRK: keep one text view per editorPane. If not we get
    //             exceptions like:
    //       com.raelity.jvi.MarkOrphanException: Mark Document Change
    //             but not very often. The whole textview thing needs...
    
    // The ViTextView will usually reference the JEditorPane, thus WeakRef.
    // If the ViTextView is reclaimed, it can always be recreated without loss.
    Map<JEditorPane,WeakReference<ViTextView>> textViews
            = new WeakHashMap<JEditorPane,WeakReference<ViTextView>>();
    
    public NbFactory() {
        super((CommandLine)null);
    }
    
    public ViFS getFS() {
        return fs;
    }
    
    public Preferences getPreferences() {
        // return NbPreferences.forModule(ViManager.class); Not in 5.5
        Preferences retValue;
        
        retValue = super.getPreferences();
        return retValue;
    }
    
    public ViTextView getViTextView(JEditorPane editorPane) {
        ViTextView tv01 = null;
        WeakReference<ViTextView> ref01 = textViews.get(editorPane);
        if(ref01 != null) {
            tv01 = ref01.get();
        }
        if(tv01 == null) {
            tv01 = new NbTextView(editorPane);
            tv01.setWindow(new Window(tv01));
            textViews.put(editorPane, new WeakReference<ViTextView>(tv01));
        }
        
        return tv01;
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
    
    //static private ChangeListener registryListener = new ChangeListe
    
    private static void startupNbVi() {
        
        // Monitor activations/opens/closes.
        // NEEDSWORK: in NB6.0 may be able to monitor WindowManager Mode.
        //            something like WindowManager.findMode("editor").addPropertyChangeListener
        //            or org.netbeans.editor.Registry monitoring.
        TopComponent.getRegistry().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if(G.dbgEditorActivation.getBoolean()) {
                    System.err.println("NbVi REG evt = " + evt.getPropertyName() + ": "
                            + evt.getOldValue()
                            + " --> " + evt.getNewValue());
                }
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
        
        Registry.addChangeListener(registryListener);
    }
    
    private static ChangeListener registryListener = new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
            if(G.dbgEditorActivation.getBoolean()) {
		System.err.println("Registry ChangeEvent: " + e);
            }
        }
    };
}
