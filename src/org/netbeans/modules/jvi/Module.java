package org.netbeans.modules.jvi;

import com.raelity.jvi.ColonCommands;
import com.raelity.jvi.G;
import com.raelity.jvi.Options;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.swing.KeyBinding;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.netbeans.editor.Registry;
import org.openide.modules.ModuleInstall;
import javax.swing.JEditorPane;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Initialization and various editor kits. Use keybindings in future releases
 * so we should not need the editor ktis.
 * <p>
 * TODO: cover all the MIME types
 */
public class Module extends ModuleInstall
{
    /** called when the module is loaded (at netbeans startup time) */
    public void restored() {
        try {
            EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                    initJVi();
                }
            });
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        } catch (InvocationTargetException ex) {
            ex.printStackTrace();
        }
    } 
    
    private static boolean didInit;
    /** Return true if have done module initialization */
    public static boolean isInit() {
        return didInit;
    }
    
    private static boolean didOptionsInit;
    /** Somehow NBOptionsl.init() causes java default keybinding to get lost,
     * if it is run too early. In this class we defer this initialization
     *  until after the first editor TC gets activated.
     * <p>This even happens if ieHACK is removed.
     */
    private static final void doOptionsInitHack() {
        if(didOptionsInit)
            return;
        didOptionsInit = true;
        NbOptions.init(); // HORROR STORY
    }
    
    private void initJVi() {
        didInit = true;
        ViManager.setViFactory(new NbFactory());
        
        Options.init();
	NbColonCommands.init();
        // NbOptions.init(); HORROR STORY
        
        // Monitor activations/opens/closes.
        // NEEDSWORK: in NB6.0 may be able to monitor WindowManager Mode.
        //            WindowManager.findMode("editor").addPropertyChangeListener
        //            or org.netbeans.editor.Registry monitoring.
        TopComponent.getRegistry().addPropertyChangeListener(
                new TopComponentRegistryListener());
        
        //
        // Some debug commands
        //
        ColonCommands.register("optionsDump", "optionsDump", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    ViManager.getViFactory().getPreferences()
                            .exportSubtree(System.out);
                } catch (BackingStoreException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        ColonCommands.register("buffersDump","buffersDump", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.err.println("Buffers activation:");
                int i = 1;
                while(true) {
                    TopComponent tc = (TopComponent)ViManager.getTextBuffer(i);
                    if(tc == null)
                        break;
                    System.err.println("\t" + tc.getDisplayName());
                    i++;
                }
                System.err.println("Buffers MRU:");
                i = 0;
                while(true) {
                    TopComponent tc = (TopComponent)ViManager.getMruBuffer(i);
                    if(tc == null)
                        break;
                    System.err.println("\t" + tc.getDisplayName());
                    i++;
                }
            }
        });
        ColonCommands.register("optionsDelete", "optionsDelete", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    Preferences prefs = ViManager.getViFactory().getPreferences();
                    String keys[] = prefs.keys();
                    for (String key : keys) {
                        prefs.remove(key);
                    }
                    prefs = prefs.node(KeyBinding.PREF_KEYS);
                    keys = prefs.keys();
                    for (String key : keys) {
                        prefs.remove(key);
                    }
                } catch (BackingStoreException ex) {
                    ex.printStackTrace();
                }
            }
        });
        ColonCommands.register("registryDump", "registryDump", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.err.println(Registry.registryToString());
            }
        });
        ColonCommands.register("topcomponentDump", "topcomponentDump", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Set<TopComponent> s = TopComponent.getRegistry().getOpened();
                System.err.println("TopComponents:");
                for (TopComponent tc : s) {
                    if(tc == null) continue;
                    System.err.print("    tc = " + tc.getDisplayName() );
                    System.err.println(", " + tc);
                }
            }
        });
        
        /*
        WindowManager.getDefault().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if(G.dbgEditorActivation.getBoolean()) {
		    System.err.println("WM evt = " + evt.getPropertyName() + ": "
			    + dispName(evt.getOldValue())
			    + " --> " + dispName(evt.getNewValue()));
                }
            }
        });
         */
    }

    /** called when an editor component is being loaded */
    public static void setupEditorPane( final JEditorPane editorPane )
    { 
        /* This doesn't do anything.  It calls setKeymap() on the JEditorPane,
         * but getKeymap() is overridden in the netbeans editor kit to return
         * a newly constructed MultiKeymap object.  So, we need to subclass
         * MultiKeymap, delegate the calls to the JVI keymap, and override
         * getKeymap() in our EditorKit classes.
         */
        //ViManager.installKeymap(editorPane);
    } 
    
    /** This class monitors the TopComponent registry and issues
     * <ul>
     * <li> ViManager.activateFile("debuginfo", tc) </li>
     * <li> ViManager.deactivateFile(ep, tc) </li>
     * <li> ViManager.exitInputMode() </li>
     * </ul>
     */
    private static class TopComponentRegistryListener
    implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            if(false && G.dbgEditorActivation.getBoolean()) {
                System.err.println("NbVi REG evt = " + evt.getPropertyName() + ": "
                        + evt.getOldValue()
                        + " --> " + evt.getNewValue());
            }
            //
            // For NB6 use PROP_TC_OPENED, PROP_TC_CLOSED
            if(evt.getPropertyName().equals(TopComponent.Registry.PROP_ACTIVATED)) {
                TopComponent oldTc = getEdTc(evt.getOldValue());
                TopComponent newTc = getEdTc(evt.getNewValue());
                
                if(oldTc != null) {
                    // Do this so don't hold begin/endUndo
                    System.err.println("TC Activated old: exit input mode: "
                                + oldTc.getDisplayName());
                    ViManager.exitInputMode();
                }
                
                if(newTc != null) {
                    ViManager.activateFile("P_ACTV", newTc);
                    doOptionsInitHack(); // HORROR STORY
                }
            } else if(evt.getPropertyName().equals(TopComponent.Registry.PROP_OPENED)) {
                // For each top component we know about, see if it is still
                // opened.
                // NEEDSWORK: checking each buffer, this seems wasteful
                // NEEDSWORK: use the org.netbeans.editor.Registry ??
                Set<TopComponent> newSet = (Set<TopComponent>)evt.getNewValue();
                Set<TopComponent> oldSet = (Set<TopComponent>)evt.getOldValue();
                if(newSet.size() > oldSet.size()) {
                    // something OPENing
                    Set<TopComponent> s = (Set<TopComponent>)((HashSet<TopComponent>)newSet).clone();
                    s.removeAll(oldSet);
                    if(s.size() != 1) {
                        System.err.println("TC OPEN: OPEN not size 1");
                    } else {
                        TopComponent tc = null;
                        for (TopComponent t : s) {
                            tc = t;
                            break;
                        }
                        tc = getEdTc(tc);
                        if(tc != null)
                            ViManager.activateFile("P_OPEN", tc);
                    }
                } else if(oldSet.size() > newSet.size()) {
                    // something CLOSEing
                    Set<TopComponent> s = (Set<TopComponent>)((HashSet<TopComponent>)oldSet).clone();
                    s.removeAll(newSet);
                    if(s.size() != 1) {
                        System.err.println("TC OPEN: CLOSE not size 1");
                    } else {
                        TopComponent tc = null;
                        for (TopComponent t : s) {
                            tc = t;
                            break;
                        }
                        // tc = getEdTc(tc); does not work, Mode is null
                        JEditorPane ep = (JEditorPane)tc.getClientProperty(
                                        NbFactory.PROP_JEP);
                        // ep is null if never registered the editor pane
                        ViManager.deactivateFile(ep, tc);
                    }
                } else
                    System.err.println("TC OPEN: SAME SET SIZE");
            }
        }
    }
    
    /**
     * Convert a TopComponent we're willing to deal with to a
     * convenient file object. The TopComponent must be Mode "editor".
     */
    private static TopComponent getEdTc(Object o) {
        if(o instanceof TopComponent) {
            TopComponent tc = (TopComponent)o;
            Mode m = WindowManager.getDefault().findMode(tc);
            String mode = m == null ? "null" : m.getName();
            if("editor".equals(mode)) {
                return tc;
            }
        }
        return null;
    }

}
