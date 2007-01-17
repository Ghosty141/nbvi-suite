package org.netbeans.modules.jvi;

import com.raelity.jvi.BooleanOption;
import com.raelity.jvi.ColonCommands;
import com.raelity.jvi.G;
import com.raelity.jvi.Options;
import com.raelity.jvi.ViManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import org.openide.modules.ModuleInstall;
import javax.swing.JEditorPane;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * TODO: selected text (with mouse) doesn't change color
 * TODO: regex integration
 *       jvi was written before jdk1.4, and there are multiple integrations
 *       with different regex libs - add one for the std regex lib
 * TODO: code folding gone away?
 * TODO: hook up actions to line-command mode (:w, etc)
 * TODO: cover all the MIME types
 * TODO: make sure non-vi keybindings still work (CTRL-X for cut, etc)
 */
public class Module extends ModuleInstall
{
    /** called when the module is loaded (at netbeans startup time) */
    public void restored()
    { 
	NbColonCommands.init();

        ViManager.setViFactory(new NbFactory());
        
        ColonCommands.register("optionsDump", "optionsDump", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    ViManager.getViFactory().getPreferences().exportSubtree(System.out);
                } catch (BackingStoreException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        ColonCommands.register("optionsDelete", "optionsDelete", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    String keys[] = ViManager.getViFactory().getPreferences().keys();
                    for (String key : keys) {
                        ViManager.getViFactory().getPreferences().remove(key);
                    }
                } catch (BackingStoreException ex) {
                    ex.printStackTrace();
                }
            }
        });
        // NEEDSWORK: change the undo strategy, can get rid of this when
        // undo/filelock issues are resolved. See NbStatusDisplay.java
        ColonCommands.register("uu", "uu", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                G.isClassicUndo.setBoolean(!G.isClassicUndo.getBoolean());
            }
        });
        
        WindowManager.getDefault().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if(G.dbgEditorActivation.getBoolean()) {
		    System.err.println("WM evt = " + evt.getPropertyName() + ": "
			    + dispName(evt.getOldValue())
			    + " --> " + dispName(evt.getNewValue()));
                }
            }
        });
        /*TopComponent.getRegistry().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println("REG evt = " + evt.getPropertyName() + ": "
                        + dispName(evt.getOldValue())
                        + " --> " + dispName(evt.getNewValue()));
            }
        });*/
    } 
    private String dispName(Object o) {
        if(o == null) return "null";
        if(o instanceof Mode) {
              String oName = o == null ? "nullobj"
                      : ((Mode)o).getName();
              return oName;
        }
        //return o.getClass().getSimpleName();
        return o.toString();
    }

    /** called when an editor component is being loaded */
    private static boolean outputTC = true;
    public static void setupEditorPane( final JEditorPane editorPane )
    { 
        ViManager.registerEditorPane(editorPane);
        if(outputTC) {
            Set<TopComponent> s = TopComponent.getRegistry().getOpened();
            System.err.println("TopComponents:");
            for (TopComponent tc : s) {
                if(tc == null) continue;
                outputTC = false;
                System.err.print("    tc = " + tc.getDisplayName() );
                System.err.println(", " + tc);
            }
        }
        
        if(false) {
            Set<Mode> s = WindowManager.getDefault().getModes();
            System.err.println("Modes: ");
            for (Mode m : s) {
                System.err.print("    m = " + m.getName());
                System.err.println(", " + m);
            }
        }
        

        /* This doesn't do anything.  It calls setKeymap() on the JEditorPane,
         * but getKeymap() is overridden in the netbeans editor kit to return
         * a newly constructed MultiKeymap object.  So, we need to subclass
         * MultiKeymap, delegate the calls to the JVI keymap, and override
         * getKeymap() in our EditorKit classes.
         */
        //ViManager.installKeymap(editorPane);
    } 

    // This does nothing
    private static void setupEditorListener() {
        Mode m = WindowManager.getDefault().findMode("editor");
        if(m != null) {
            m.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    System.err.println("Editor Mode Event = " + evt );
                }
            });
        }
    }
}
