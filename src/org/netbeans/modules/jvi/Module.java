package org.netbeans.modules.jvi;

import com.raelity.jvi.ColonCommands;
import com.raelity.jvi.G;
import com.raelity.jvi.Options;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.swing.KeyBinding;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.netbeans.editor.Registry;
import org.openide.modules.ModuleInstall;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Caret;
import org.netbeans.editor.MultiKeymap;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.html.HTMLKit;
import org.netbeans.modules.editor.java.JavaKit;
import org.netbeans.modules.editor.plain.PlainKit;
import org.netbeans.modules.xml.text.syntax.XMLKit;
import org.openide.cookies.EditorCookie;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Initialization and various editor kits. Use keybindings in future releases
 * so we should not need the editor ktis.
 * <p>
 * TODO: cover all the MIME types
 */
public class Module extends ModuleInstall {
    /** called when the module is loaded (at netbeans startup time) */
    public void restored() {
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
    
    private static final void initJVi() {
        assert(EventQueue.isDispatchThread());
        if(didInit)
            return;
        didInit = true;
        
        ViManager.setViFactory(new NbFactory());
        
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
            assert(EventQueue.isDispatchThread());
            if(false && G.dbgEditorActivation.getBoolean()) {
                System.err.println("NbVi REG evt = " + evt.getPropertyName() + ": "
                        + evt.getOldValue()
                        + " --> " + evt.getNewValue());
            }
            //
            // For NB6 use PROP_TC_OPENED, PROP_TC_CLOSED
            if(evt.getPropertyName().equals(TopComponent.Registry.PROP_ACTIVATED)) {
                tcDumpInfo(evt.getOldValue(), "activated oldTC");
                tcDumpInfo(evt.getNewValue(), "activated newTC");
                
                TopComponent oldTc = getEdTc(evt.getOldValue());
                TopComponent newTc = getEdTc(evt.getNewValue());
                
                if(oldTc != null) {
                    // Do this so don't hold begin/endUndo
                    if(G.dbgEditorActivation.getBoolean()) {
                        System.err.println("TC Activated old: exit input mode: "
                                           + oldTc.getDisplayName());
                    }
                    ViManager.exitInputMode();
                }
                
                if(newTc != null) {
                    ViManager.activateFile("P_ACTV", newTc);
                    doOptionsInitHack(); // HORROR STORY
                }
            } else if(evt.getPropertyName().equals(TopComponent.Registry.PROP_OPENED)) {
                // For each top component we know about, see if it is still
                // opened.
                // NEEDSWORK: checking each buffer (until NB6 PROP_TC_OPENED)
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
                        tcDumpInfo(tc, "open");
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
                        tcDumpInfo(tc, "close");
                        ViManager.deactivateFile(ep, tc);
                    }
                } else
                    System.err.println("TC OPEN: SAME SET SIZE");
            }
        }
    }
    
    private static String ancestorStringTC(Object o) {
        StringBuilder s = new StringBuilder();
        TopComponent tc = null;
        TopComponent parent = (TopComponent)SwingUtilities
                .getAncestorOfClass(TopComponent.class, (Component)o);
        while (parent != null) {
            s.append(" ")
                .append(parent.getDisplayName())
                .append(":")
                .append(parent.hashCode());
            tc = parent;
            parent = (TopComponent)SwingUtilities.getAncestorOfClass(TopComponent.class, tc);
        }
        return s.toString();
    }
    
    private static final void tcDumpInfo(Object o, String tag) {
        if(!G.dbgEditorActivation.getBoolean())
            return;
        if(!(o instanceof TopComponent))
            return;
        TopComponent tc = (TopComponent) o;
        EditorCookie ec = (EditorCookie)tc.getLookup().lookup(EditorCookie.class);
        if(ec == null)
            return;
        JEditorPane panes [] = ec.getOpenedPanes();
        System.err.println("trackTC: " + tag + ": "
                           + tc.getDisplayName() + ":" + tc.hashCode()
                           + ": nPanes = "
                           + (panes == null ? "null" : panes.length));
        if(panes != null) {
            for (JEditorPane ep : panes) {
                System.err.println("\tep " + ep.hashCode() + ancestorStringTC(ep));
            }
        }
    }
    
    /** @return true if the TopComponent contains an JEditorPane */
    public static boolean containsEP(TopComponent tc) {
        TopComponent tc01 = null;
        EditorCookie ec = (EditorCookie)tc.getLookup().lookup(EditorCookie.class);
        if(ec == null)
            return false;
        
        JEditorPane panes [] = ec.getOpenedPanes();
        if(panes != null) {
            for (JEditorPane ep : panes) {
                Container parent = SwingUtilities
                        .getAncestorOfClass(TopComponent.class, ep);
                while (parent != null) {
                    tc01 = (TopComponent)parent;
                    if(tc == tc01)
                        return true;
                    parent = SwingUtilities.getAncestorOfClass(TopComponent.class,
                                                               tc01);
                }
                // NOTE: could break if only want to check the first
            }
        }
        
        return false;
    }
    
    /**
     * This method investigates a TC to see if it is something that jVi 
     * wants to work with. If it has Mode "editor" or has editorPanes,
     * then its one we want. Assuming one of the associated editorPanes is a
     * decendant of the TopComponent.
     *
     * TopComponents in a secondary editor window are not Mode "editor",
     * and MVTC do not have panes at open time, not until they are acivated.
     *
     * @return the TC if it is interesting, else null
     */
    private static TopComponent getEdTc(Object o) {
        TopComponent tc = null;
        if(o instanceof TopComponent) {
            tc = (TopComponent)o;
            
            /* Forget the mode editor, at least for now.
             *
            Mode m = WindowManager.getDefault().findMode(tc);
            String mode = m == null ? "null" : m.getName();
            if("editor".equals(mode)) {
                return tc;
            }
             */
            
        }
        // If it's not mode editor, it may have panes
        if(tc != null) {
            if(containsEP(tc))
                return tc;
        }
        return null;
    }
    
    //
    // Here are the various editor kits.
    // Expect to get rid of them entirely when jVi is a keybindings only thing.
    //
    
    public static class ViKit extends NbEditorKit {
        public ViKit() { super(); initJVi(); }
        
        public MultiKeymap getKeymap() {
            return new NbKeymap(super.getKeymap(), KeyBinding.getKeymap());
        }
        
        public Caret createCaret() { return new NbCaret(); }
    }
    
    public static class PlainViKit extends PlainKit {
        public PlainViKit() { super(); initJVi(); }
        
        public MultiKeymap getKeymap() {
            return new NbKeymap(super.getKeymap(), KeyBinding.getKeymap());
        }
        
        public Caret createCaret() { return new NbCaret(); }
    }
    
    public static class HtmlViKit extends HTMLKit {
        public HtmlViKit() { super(); initJVi(); }
        
        public MultiKeymap getKeymap() {
            return new NbKeymap(super.getKeymap(), KeyBinding.getKeymap());
        }
        
        public Caret createCaret() { return new NbCaret(); }
    }
    
    public static class JavaViKit extends JavaKit {
        public JavaViKit() { super(); initJVi(); }
        
        public MultiKeymap getKeymap() {
            return new NbKeymap( super.getKeymap(), KeyBinding.getKeymap());
        }
        
        public Caret createCaret() { return new NbCaret(); }
    }
    
    public static class XMLViKit extends XMLKit {
        public XMLViKit() { super(); initJVi(); }
        
        public MultiKeymap getKeymap() {
            return new NbKeymap(super.getKeymap(), KeyBinding.getKeymap());
        }
        
        public Caret createCaret() { return new NbCaret(); }
    }
}
