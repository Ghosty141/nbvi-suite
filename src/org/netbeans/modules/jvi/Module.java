package org.netbeans.modules.jvi;

import com.raelity.jvi.BooleanOption;
import com.raelity.jvi.ColonCommands;
import com.raelity.jvi.G;
import com.raelity.jvi.Options;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.swing.KeyBinding;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.text.Caret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.TextAction;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseKit;
import org.netbeans.editor.Registry;
import org.netbeans.editor.Settings;
import org.netbeans.editor.ext.ExtCaret;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.modules.ModuleInstall;
import javax.swing.JEditorPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.SettingsNames;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.Repository;
import org.openide.loaders.DataObject;
import org.openide.util.HelpCtx;
import org.openide.util.actions.Presenter;
import org.openide.util.actions.SystemAction;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Initialization/install/uninstall, TopComponent tracking,
 * KeyBinding installation, some colon commands for state output.
 * The initialization has three components:
 * <ol>
 *   <li>Enable/disable jVi. This is independent of the module install stuff.
 *       jVi can be started/stopped while the module is instaelled. </li>
 *   <li>Early init. The stuff that should be done when the module is first
 *       installed and/or accessed, eg start up listeners, prepare jVi for use.
 *       Since jVi might be accessed before it is installed. There is more in
 *       here than seems right. But it may be that I don't understand the
 *       startup very well.</li>
 *   <li>Stuff that can be done later</li>
 * </ol>
 */
public class Module extends ModuleInstall {
    
    private static boolean jViEnabled;
    
    public static final String PROP_JEP = "ViJEditorPane";
    
    // The persistent option names and their variables
    public static final String DBG_MODULE = "DebugNbModule";
    public static final String DBG_TC = "DebugNbTopComponent";
    static BooleanOption dbgNb;
    static BooleanOption dbgAct;
    
    private static TopComponentRegistryListener topComponentRegistryListener;
    private static KeyBindingsFilter keyBindingsFilter;
    
    private static final String JVI_INSTALL_ACTION_NAME = "jvi-install";
    private static Map<Class, Action> kitToDefaultKeyAction
            = new HashMap<Class, Action>();
    private static Map<JEditorPane, Caret> editorToCaret
            = new HashMap<JEditorPane, Caret>(); // NB6 don't want this
    
    /** called when the module is loaded (at netbeans startup time) */
    public void restored() {
        if(dbgNb != null && dbgNb.getBoolean())
            System.err.println("********* restored *********");
        earlyInit();
            
        JViEnableAction jvi
                = (JViEnableAction)SystemAction.get(JViEnableAction.class);
        jvi.setSelected(true);
        runInDispatch(runJViEnable, true);
    }

    public void uninstalled() {
        super.uninstalled();
        
        if(dbgNb.getBoolean())
            System.err.println("********* uninstalled *********");
        
        JViEnableAction jvi
                = (JViEnableAction)SystemAction.get(JViEnableAction.class);
        jvi.setSelected(false);
        runInDispatch(runJViDisable, true);
    }
    
    private static Runnable runJViEnable = new Runnable() {
        public void run() {
            if(jViEnabled)
                return;
            
            if(dbgNb.getBoolean())
                System.err.println("Module: runJViEnable");
            
            jViEnabled = true;
            
            // Monitor activations/opens/closes.
            // NEEDSWORK: in NB6.0 may be able to monitor WindowManager Mode.
            //        WindowManager.findMode("editor").addPropertyChangeListener
            //        or org.netbeans.editor.Registry monitoring.
            if(topComponentRegistryListener == null) {
                topComponentRegistryListener = new TopComponentRegistryListener();
                TopComponent.getRegistry().addPropertyChangeListener(
                        topComponentRegistryListener);
            }
            
            // See if there's anything to fire up
            Set<TopComponent> s = TopComponent.getRegistry().getOpened();
            for (TopComponent tc : s) {
                JEditorPane ep = getTCEditor(tc);
                if(ep != null)
                    activateTC(ep, tc, "ENABLE");
            }
            
            if(keyBindingsFilter == null) {
                keyBindingsFilter = new KeyBindingsFilter();
                Settings.addFilter(keyBindingsFilter);
            }
            
            Settings.reset();
        }
    };
    
    private static Runnable runJViDisable = new Runnable() {
        public void run() {
            if(!jViEnabled)
                return;
            
            jViEnabled = false;
            
            if(dbgNb.getBoolean())
                System.err.println("Module: runJViDisable");
            
            if(topComponentRegistryListener != null)
                TopComponent.getRegistry().removePropertyChangeListener(
                        topComponentRegistryListener);
            
            if(keyBindingsFilter != null) {
                KeyBindingsFilter f = keyBindingsFilter;
                keyBindingsFilter = null;
                Settings.removeFilter(f);
            }
            
            // remove all jVi connections, replace original caret
            TopComponent tc = (TopComponent)ViManager.getTextBuffer(1);
            while(tc != null) {
                JEditorPane ep = (JEditorPane)tc.getClientProperty(PROP_JEP);
                Caret c01 = editorToCaret.get(ep);
                if(c01 != null) {
                    Caret c02 = ep.getCaret();
                    if(c02 instanceof NbCaret) {
                        int offset = c02.getDot();
                        int blinkRate = c02.getBlinkRate();

                        ep.setCaret(c01);
                        c01.setDot(offset);
                        c01.setBlinkRate(blinkRate);
                        if(dbgNb.getBoolean()) {
                            System.err.println("Module: restore caret: "
                                            + tc.getDisplayName());
                        }
                    }
                }
                closeTC(ep, tc);
                tc = (TopComponent)ViManager.getTextBuffer(1);
            }
            
            Settings.reset();
        }
    };
    
    private static boolean didOptionsInit;
    /** Somehow NbOptions.init() causes java default keybinding to get lost,
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
    
    private static boolean didEarlyInit = false;
    private static synchronized void earlyInit() {
        if(didEarlyInit)
            return;
        didEarlyInit = true;
        
        runInDispatch(runEarlyInit, true);
    }
    
    private static Runnable runEarlyInit = new Runnable() {
        public void run() {
            ViManager.setViFactory(new NbFactory());
            
            dbgNb = Options.createBooleanOption(DBG_MODULE, false);
            Options.setupOptionDesc(DBG_MODULE, "Module interface",
                                    "Module and editor kit install/install");
            
            dbgAct = Options.createBooleanOption(DBG_TC, false);
            Options.setupOptionDesc(DBG_TC, "Top Component",
                                    "TopComponent activation/open");
            
            ViManager.addStartupListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    initJVi(); // can wait a little while for this
                }
            });
        }
    };
    
    /** Return true if have done module initialization */
    public static boolean isInit() {
        return didInit;
    }
    
    private static boolean didInit;
    
    private synchronized static void initJVi() {
        if(didInit)
            return;
        didInit = true;
        
        runInDispatch(runInitJVi, true);
    }
    
    private static Runnable runInitJVi = new Runnable() {
        public void run() {
            earlyInit();
            
            NbColonCommands.init();
            // NbOptions.init(); HORROR STORY
            
            addDebugColonCommands();
        }
    };
    
    private static void addDebugColonCommands() {
        
        //
        // Some debug commands
        //
        ColonCommands.register("optionsDump", "optionsDump", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    ViManager.getViFactory().getPreferences().exportSubtree(os);
                    ViOutputStream vios = ViManager.createOutputStream(
                            null, ViOutputStream.OUTPUT, "Preferences");
                    vios.println(os.toString());
                    vios.close();
                    
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
                    prefs = prefs.node(ViManager.PREFS_KEYS);
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
        ColonCommands.register("kitDump", "kitDump", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Map<Class, Action> m = kitToDefaultKeyAction;
                ViOutputStream os = ViManager.createOutputStream(
                        null, ViOutputStream.OUTPUT, "Known Kits");
                for (Map.Entry<Class, Action> entry
                        : kitToDefaultKeyAction.entrySet()) {
                    os.println(String.format("%20s %s",
                               entry.getKey().getSimpleName(),
                               entry.getValue().getClass().getSimpleName()));
                }
                os.close();
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
    
    public static void execFileSystemAction(String path, ActionEvent e) {
        Action act = fetchFileSystemAction(path);
        if(act != null && act.isEnabled())
            act.actionPerformed(e);
        else
            Util.vim_beep();
    }
    
    /** Get an Action from the file system at the given path.
     * Check if it is a SystemAction, if not then try to create it.
     * @return an Action, null if couldn't get or create one
     */
    public static Action fetchFileSystemAction(String path) {
        FileObject fo = Repository.getDefault().getDefaultFileSystem()
                                                .getRoot().getFileObject(path);
        if(fo == null)
            return null;
        InstanceCookie ck = null;
        Action act = null;
        try {
            ck = (InstanceCookie) DataObject.find(fo)
                                    .getCookie(InstanceCookie.class);
        } catch (DataObjectNotFoundException ex) {
        }
        if(ck != null) {
            try {
                act = SystemAction.get(ck.instanceClass());
                if(act != null)
                    return act;
            } catch (Exception ex) { }
        }
        if(act == null) {
            // if its not a SystemAction try creating one
            Object o = null;
            try {
                o = ck.instanceCreate();
            } catch (Exception ex) { }
            if(o instanceof Action)
                act = (Action) o;
        }
        return act;
    }
    
    public static final Action getDefaultKeyAction(Class clazz) {
        Map<Class, Action> m = kitToDefaultKeyAction;
        return m.get(clazz);
    }
    
    static void updateKeymap() {
        Settings.touchValue(BaseKit.class, SettingsNames.KEY_BINDING_LIST);
    }
    
    /**
     * Hook into Settings to add jVi bindings and actions for the Keymap;
     * keep track of editor kit's original defaultKeyTypedAction.
     */
    private static final class KeyBindingsFilter implements Settings.Filter {
        
        public Settings.KitAndValue[] filterValueHierarchy(
                                            Class kitClass,
                                            String settingName,
                                            Settings.KitAndValue[] kavArray) {
            if(!jViEnabled)
                return kavArray;
            
            if(!(settingName.equals(SettingsNames.KEY_BINDING_LIST)
                 || settingName.equals(SettingsNames.CUSTOM_ACTION_LIST)
                 || settingName.equals(SettingsNames.KIT_INSTALL_ACTION_NAME_LIST)))
                return kavArray;
            
            if(false) {
                System.err.println("KeyBindingsFilter: " + settingName + ": "
                                    + kitClass.getSimpleName());
            }

            // got a live one, augment keybindings or actions
            Settings.KitAndValue kv01 = new Settings.KitAndValue(null, null);
            
            if(settingName.equals(SettingsNames.KEY_BINDING_LIST)) {
                List<JTextComponent.KeyBinding> l
                        = (List)((ArrayList)KeyBinding.getBindingsList()).clone();
                
                // Going through this path, in the end result, the only bindings
                // for "caret-up/down" are VK_KP_UP/DOWN. Code completion uses
                // "caret-up" for scrolling, and VK_UP/DOWN is what is needed.
                // jVi doesn't use "caret-up" but somewhere they are getting
                // trashed. They are probably getting trashed because we are
                // providing a mapping for VK_UP, and the same key can not be
                // mapped to multiple events; so VK_UP --> "caret-up" is removed
                // So add mappings for VK_KP_UP/DOWN, then there will be no
                // bindings for "caret-up" and the defaults for code comletion
                //will be used. (This means that VK_KP_UP won't work, sigh.)
                // (see CompletionScrollPane if you're interested)
                l.add(new JTextComponent.KeyBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0),
                                               "ViUpKey"));
                l.add(new JTextComponent.KeyBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, 0),
                                               "ViDownKey"));
                kv01.value = l;
            }
            else if(settingName.equals(SettingsNames.CUSTOM_ACTION_LIST)) {
                // get the jVi keybindings
                List<Action> l =
                        (List)((ArrayList)KeyBinding.getActionsList()).clone();
                
                // Add an action that gets invoked when editor kit install
                l.add(new JViInstallAction());
                
                // Want to add jVi defaultKeyTypedAction, but can't do that
                // until the kit's default keytyped action is stashed for
                // later use. Note: if the kit wants to change its
                // defaultKeyTypedAction, that is not detected. Could probably
                // work something out, but beware infinite loops.
                if(getDefaultKeyAction(kitClass) != null)
                    l.add(ViManager.getViFactory().createCharAction(
                                    DefaultEditorKit.defaultKeyTypedAction));
                kv01.value = l;
            }
            else if(settingName.equals(
                            SettingsNames.KIT_INSTALL_ACTION_NAME_LIST)) {
                List<String> l = new ArrayList<String>();
                l.add(JVI_INSTALL_ACTION_NAME);
                kv01.value = l;
            }
            
            // jVi goes first in array to overide anything else
            Settings.KitAndValue kv[] = null;
            if(kv01.value != null) {
                kv01.kitClass = ViManager.class; // why not?
                kv = new Settings.KitAndValue[kavArray.length+1];
                System.arraycopy(kavArray, 0, kv, 1, kavArray.length);
                kv[0] = kv01;
            } else
                kv = kavArray;
            return kv;
        }

        public Object filterValue(Class kitClass,
                                  String settingName,
                                  Object value) {
            return value;
        }
    }
    
    private static class JViInstallAction extends TextAction {
        JViInstallAction() {
            super(JVI_INSTALL_ACTION_NAME);
            putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        }
        
        public void actionPerformed(ActionEvent e) {
            EditorKit kit = ((JEditorPane)e.getSource()).getEditorKit();
            if(dbgNb.getBoolean()) {
                System.err.println("Module: kit installed: "
                                   + kit.getClass().getSimpleName());
            }
            Action a = kitToDefaultKeyAction.get(kit.getClass());
            if(a == null && kit instanceof BaseKit) {
                a = ((BaseKit)kit).getActionByName(BaseKit.defaultKeyTypedAction);
                kitToDefaultKeyAction.put(kit.getClass(), a);
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        Settings.touchValue(BaseKit.class,
                                            SettingsNames.CUSTOM_ACTION_LIST);
                    }
                });
            }
        }
    }

    /**
     * This class delegates an action to an Action which is found
     * in the file system.
     */
    public static class DelegateFileSystemAction implements ActionListener {
        String actionPath;
        DelegateFileSystemAction(String actionPath) {
            this.actionPath = actionPath;
        }
        
        public void actionPerformed(ActionEvent e) {
            execFileSystemAction(actionPath, e);
        }
    }
    
    private static void closeTC(JEditorPane ep, TopComponent tc) {
        ViManager.closeFile(ep, tc);
        editorToCaret.remove(ep);
    }
    
    private static void activateTC(JEditorPane ep, Object o, String tag) {
        TopComponent tc = (TopComponent)o;
        if(ep != null && editorToCaret.get(ep) == null) {
            if(dbgNb.getBoolean()) {
                System.err.println("Module: capture caret: "
                                   + ((TopComponent)tc).getDisplayName());
            }
            editorToCaret.put(ep, ep.getCaret());
        }
        if(ep != null && tc != null)
            tc.putClientProperty(PROP_JEP, ep);
        ViManager.activateFile(ep, tc, tag);
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
            if(false && dbgAct.getBoolean()) {
                System.err.println("NbVi REG evt = " + evt.getPropertyName() + ": "
                        + evt.getOldValue()
                        + " --> " + evt.getNewValue());
            }
            //
            // For NB6 use PROP_TC_OPENED, PROP_TC_CLOSED
            if(evt.getPropertyName().equals(TopComponent.Registry.PROP_ACTIVATED)) {
                tcDumpInfo(evt.getOldValue(), "activated oldTC");
                tcDumpInfo(evt.getNewValue(), "activated newTC");
                
                if(getTCEditor(evt.getOldValue()) != null)
                    ViManager.deactivateCurrentFile(evt.getOldValue());
                
                JEditorPane ep = getTCEditor(evt.getNewValue());
                if(ep != null) {
                    activateTC(ep, evt.getNewValue(), "P_ACTV");
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
                        JEditorPane ep = getTCEditor(tc);
                        if(ep != null)
                            activateTC(ep, tc, "P_OPEN");
                    }
                } else if(oldSet.size() > newSet.size()) {
                    // something CLOSEing
                    Set<TopComponent> s = (Set<TopComponent>)
                                ((HashSet<TopComponent>)oldSet).clone();
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
                                                                    PROP_JEP);
                        // ep is null if never registered the editor pane
                        tcDumpInfo(tc, "close");
                        closeTC(ep, tc);
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
        JEditorPane panes[] = null;
        if(ec != null)
            panes = ec.getOpenedPanes();
        Mode mode = WindowManager.getDefault().findMode(tc);
        if(dbgAct.getBoolean()) {
            System.err.println("trackTC: " + tag + ": "
                        + tc.getDisplayName() + ":" + tc.hashCode()
                        + " '" + (mode == null ? "null" : mode.getName()) + "'"
                        + (ec == null ? " ec null"
                           : " : nPanes = "
                             + (panes == null ? "null" : panes.length)));
        }
        if(panes != null) {
            for (JEditorPane ep : panes) {
                if(dbgAct.getBoolean()) {
                    System.err.println("\tep " + ep.hashCode()
                                       + ancestorStringTC(ep));
                }
            }
        }
    }
    
    /**
     * Investigate the argument Object and return a
     * contained JEditorPane.
     *
     * This method investigates a TC to see if it is something that jVi 
     * wants to work with. Assuming one of the associated editorPanes is a
     * decendant of the TopComponent.
     *
     * Note: MVTC do not have panes at open time, not until they are acivated.
     * Note: can not count on TC's Mode.
     * Note: simply having an associated JEditorPane is insuficient, for
     * example a navigator may be associated with a JEditorPane.
     *
     * @return The contained editor or null if there is none.
     */
    public static JEditorPane getTCEditor(Object o) {
        TopComponent tc = null;
        if(!(o instanceof TopComponent)) {
            return null;
        }
        tc = (TopComponent) o;
        EditorCookie ec = (EditorCookie)tc.getLookup().lookup(EditorCookie.class);
        if(ec == null)
            return null;
        
        JEditorPane panes [] = ec.getOpenedPanes();
        if(panes != null) {
            for (JEditorPane ep : panes) {
                Container parent = SwingUtilities
                        .getAncestorOfClass(TopComponent.class, ep);
                while (parent != null) {
                    Container c01 = parent;
                    if(tc == c01)
                        return ep;
                    parent = SwingUtilities.getAncestorOfClass(TopComponent.class,
                                                               c01);
                }
            }
        }
        
        return null;
    }
    
    public static void runInDispatch(Runnable runnable, boolean wait) {
        if(EventQueue.isDispatchThread()) {
            runnable.run();
        } else if(!wait) {
            EventQueue.invokeLater(runnable);
        } else {
                try {
                    EventQueue.invokeAndWait(runnable);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                } catch (InvocationTargetException ex) {
                    ex.printStackTrace();
                }
        }
    }
    
    private static class JViEnableAction extends SystemAction
                                         implements Presenter.Menu {
        private static final String NAME="jVi";
        private JCheckBoxMenuItem cb;
        
        JViEnableAction() {
            cb = new JCheckBoxMenuItem(NAME);
            cb.setAction(this);
        }
        
        public JMenuItem getMenuPresenter() {
            return cb;
        }
        
        boolean isSelected() {
            return cb.isSelected();
        }
        
        void setSelected(boolean b) {
            cb.setSelected(b);
        }

        public void actionPerformed(ActionEvent e) {
            if(isSelected())
                runJViEnable.run();
            else
                runJViDisable.run();
        }
        
        public HelpCtx getHelpCtx() {
            return HelpCtx.DEFAULT_HELP;
        }

        public String getName() {
            return NAME;
        }
    }
}
