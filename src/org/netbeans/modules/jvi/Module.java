package org.netbeans.modules.jvi;

import com.raelity.jvi.BooleanOption;
import com.raelity.jvi.ColonCommands;
import com.raelity.jvi.ColonCommands.ColonEvent;
import com.raelity.jvi.G;
import com.raelity.jvi.Msg;
import com.raelity.jvi.Options;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViCmdEntry;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.swing.DefaultViFactory;
import com.raelity.jvi.swing.KeyBinding;
import com.raelity.jvi.swing.ViCaret;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Caret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.TextAction;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseKit;
import org.netbeans.editor.Settings;
import org.netbeans.editor.SettingsChangeEvent;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.modules.ModuleInstall;
import javax.swing.JEditorPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.SettingsChangeListener;
import org.netbeans.editor.SettingsNames;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.ErrorManager;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.Repository;
import org.openide.loaders.DataObject;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.actions.CallableSystemAction;
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
    private static boolean nb6;

    private static final String MOD = "Module-" +
            System.identityHashCode(Module.class.getClassLoader()) + ": ";
    
    private static final String PROP_JEP = "ViJEditorPane";
    private static final String PREF_ENABLED = "viEnabled";
    
    // The persistent option names and their variables
    public static final String DBG_MODULE = "DebugNbModule";
    public static final String DBG_TC = "DebugNbTopComponent";
    private static BooleanOption dbgNb;
    private static BooleanOption dbgAct;
    
    private static TopComponentRegistryListener topComponentRegistryListener;
    private static KeyBindingsFilter keyBindingsFilter;
    
    private static final String JVI_INSTALL_ACTION_NAME = "jvi-install";
    private static Map<Class, Action> kitToDefaultKeyAction
            = new HashMap<Class, Action>();
    
    private static Map<JEditorPane, Caret> editorToCaret
            = new WeakHashMap<JEditorPane, Caret>(); // NB6 don't want this

    static {
        try {
            Lookup.getDefault().lookup(ClassLoader.class)
                .loadClass("org.openide.util.NbPreferences");
            //Class.forName("org.openide.util.NbPreferences");
            nb6 = true;
        } catch (ClassNotFoundException ex) { }
    }

    public static boolean isNb6() {
        return nb6;
    }

    static boolean jViEnabled() {
        return jViEnabled;
    }

    /** @return the module specific preferences.
     */
    public static Preferences getModulePreferences() {
        return ViManager.getViFactory().getPreferences()
                        .userNodeForPackage(Module.class);
    }
    
    /** called when the module is loaded (at netbeans startup time) */
    @Override
    public void restored() {
        if(dbgNb != null && dbgNb.getBoolean())
            System.err.println(MOD + "***** restored *****");
        earlyInit();
            
        JViEnableAction jvi = SystemAction.get(JViEnableAction.class);

        Preferences prefs = getModulePreferences();
        if(prefs.getBoolean(PREF_ENABLED, true)) {
            jvi.setSelected(true);
            runInDispatch(true, new RunJViEnable());
        } else {
            jvi.setSelected(false);
        }
    }

    @Override
    public void uninstalled() {
        super.uninstalled();
        
        if(dbgNb.getBoolean())
            System.err.println(MOD + "***** uninstalled *****");
        
        JViEnableAction jvi = SystemAction.get(JViEnableAction.class);
        jvi.setSelected(false);
        runInDispatch(true, new RunJViDisable());
    }
    
    private static class RunJViEnable implements Runnable {
        public void run() {
            if(jViEnabled)
                return;
            
            if(dbgNb.getBoolean())
                System.err.println(MOD + " runJViEnable");
            
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
            
            // See if there's anything to attach to, there are two cases to
            // consider:
            // enbable through the menu/toolbar
            //      In this case the default key typed action has already
            //      been captured for any opened editors.
            // module activation
            //      At boot, there are no opened editors. But if the module
            //      is activated after NB comes up, then there *are* editors.
            //      So capture keyType for the TopComponents. The nomads will
            //      not be captured, oh well. Could capture the nomads by
            //      checking at keyTyped, not worth it.
            //
            Set<TopComponent> s = TopComponent.getRegistry().getOpened();
            for (TopComponent tc : s) {
                JEditorPane ep = getTCEditor(tc);
                if(ep != null) {
                    captureDefaultKeyTypedAction(ep);
                    activateTC(ep, tc, "JVI-ENABLE");
                }
            }
            
            // And setup the nomads
            // nomadicEditors
            
            if(keyBindingsFilter == null) {
                keyBindingsFilter = new KeyBindingsFilter();
                Settings.addFilter(keyBindingsFilter);
            }
            
            Settings.reset();

            TabWarning.setTabWarning(true);
        }
    }
    
    /**
     * Unhook almost everything.
     * <p/>
     * In the future, may want to leave the TopComponent listener active so
     * that we can maintain the MRU list.
     */
    private static class RunJViDisable implements Runnable {
        public void run() {
            if(!jViEnabled)
                return;
            
            jViEnabled = false;
            
            // XXX NbOptions.disable();
            // XXX didOptionsInit = false;
            
            if(dbgNb.getBoolean())
                System.err.println(MOD + "runJViDisable");
            
            if(topComponentRegistryListener != null) {
                TopComponent.getRegistry().removePropertyChangeListener(
                        topComponentRegistryListener);
                topComponentRegistryListener = null;
            }
            
            if(keyBindingsFilter != null) {
                KeyBindingsFilter f = keyBindingsFilter;
                keyBindingsFilter = null;
                Settings.removeFilter(f);
            }
            
            // remove all jVi connections, replace original caret
            TopComponent tc = (TopComponent)ViManager.getTextBuffer(1);
            while(tc != null) {
                for (JEditorPane ep : fetchEpFromTC(tc)) {
                    Caret c01 = editorToCaret.get(ep);
                    if(c01 != null) {
                        if(ep.getCaret() instanceof NbCaret) {
                            NbFactory.installCaret(ep, c01);
                            if(dbgNb.getBoolean()) {
                                System.err.println(MOD + "restore caret: "
                                                + tc.getDisplayName());
                            }
                        }
                    }
                    closeTC(ep, tc);
                }
                tc = (TopComponent)ViManager.getTextBuffer(1);
            }
            
            // At this point, any remaining members of editorToCaret
            // must be nomad (or so one would think).
            // Remove any references.
            for (JEditorPane ep : editorToCaret.keySet()) {
                Caret c01 = null;
                if(ep.getCaret() instanceof ViCaret) {
                    c01 = editorToCaret.get(ep);
                    if(c01 != null)
                        NbFactory.installCaret(ep, c01);
                }
                if(dbgNb.getBoolean())
                    System.err.println(MOD + "shutdown nomad"
                            + (c01 != null ? " restore caret" : ""));
                ViManager.getViFactory().shutdown(ep);
            }
            
            for (Document doc : NbFactory.getDocSet()) {
                doc.putProperty(NbFactory.PROP_BUF, null);
            }
            
            // Following shouldn't be needed
            for (JEditorPane ep : NbFactory.getEditorSet()) {
                ep.putClientProperty(NbFactory.PROP_VITV, null);
            }
            
            if(dbgNb.getBoolean())
                ViManager.dump(System.err);
            
            Settings.reset();
        }
    }
    
    // XXX private static boolean didOptionsInit;
    /** Somehow NbOptions.init() causes java default keybinding to get lost,
     * if it is run too early. In this class we defer this initialization
     *  until after the first editor TC gets activated.
     * <p>This even happens if ieHACK is removed.
     */
    //private static final void doOptionsInitHack() {
    //    if(didOptionsInit)
    //        return;
    //    didOptionsInit = true;
    //    NbOptions.enable(); // HORROR STORY
    //}
    
    private static boolean didEarlyInit = false;
    private static synchronized void earlyInit() {
        if(didEarlyInit)
            return;
        didEarlyInit = true;
        
        runInDispatch(true, runEarlyInit);
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
        
        runInDispatch(true, runInitJVi);
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
        ColonCommands.register("optionDelete", "optionDelete",
                               new ColonCommands.ColonAction() {

            public void actionPerformed(ActionEvent ev) {
                ColonEvent cev = (ColonEvent) ev;
            
                if(cev.getNArg() == 1) {
                    String key = cev.getArg(1);
                    Preferences prefs = ViManager.getViFactory().getPreferences();
                    prefs.remove(key);
                } else
                    Msg.emsg("optionDelete takes exactly one argument");
            }
        });
        ColonCommands.register("topcomponentDump", "topcomponentDump", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Set<TopComponent> s = TopComponent.getRegistry().getOpened();
                System.err.println("TopComponents:");
                for (TopComponent tc : s) {
                    if(tc == null) continue;
                    System.err.print("    tc = " + tc.getDisplayName() );
                    System.err.print(", " + tc.isVisible());
                    System.err.println(", " + tc.getClass().getName());
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
        ColonCommands.register("checkFsActList", "checkFsActList",
                new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for (String act : FsAct.getFsActList()) {
                    if(fetchFileSystemAction(act) == null) {
                        System.err.println("Not found: " + act);
                    }
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
        if(!FsAct.getFsActList().contains(path)) {
            ViManager.dumpStack("unlisted action");
        }
        FileObject fo = Repository.getDefault().getDefaultFileSystem()
                                                .getRoot().getFileObject(path);
        if(fo == null)
            return null;
        
        InstanceCookie ck = null;
        Action act = null;
        try {
            ck = DataObject.find(fo).getCookie(InstanceCookie.class);
        } catch (DataObjectNotFoundException ex) { }
        if(ck != null) {
            try {
                if(SystemAction.class.isAssignableFrom(ck.instanceClass())) {
                    @SuppressWarnings("unchecked")
                    Class<SystemAction> sa
                            = (Class<SystemAction>)ck.instanceClass();
                    act = SystemAction.get(sa);
                }
            } catch (Exception ex) { }
            if(act == null) {
                // if its not a SystemAction try creating one
                Object o = null;
                try {
                    o = ck.instanceCreate();
                } catch (Exception ex) { }
                if(o instanceof Action)
                    act = (Action) o;
                // else if(o instanceof MainMenuAction) {
                //     MainMenuAction mma = (MainMenuAction)o;
                //     act = mma
                // }
            }
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
                        = KeyBinding.getBindingsList();
                
                // Going through this path, in the end result, the only bindings
                // for "caret-up/down" are VK_KP_UP/DOWN. Code completion uses
                // "caret-up" for scrolling, and VK_UP/DOWN is what is needed.
                // jVi doesn't use "caret-up" but somewhere they are getting
                // trashed. They are probably getting trashed because we are
                // providing a mapping for VK_UP, and the same key can not be
                // mapped to multiple events; so VK_UP --> "caret-up" is removed
                // So add mappings for VK_KP_UP/DOWN, then there will be no
                // bindings for "caret-up" and the defaults for code comletion
                // will be used. (This means that VK_KP_UP won't work, sigh.)
                // If interested see editor/completion/src/org/netbeans/modules
                //                      /editor/completion/CompletionScrollPane
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
                List<Action> l = KeyBinding.getActionsList();
                
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
            JEditorPane ep = (JEditorPane)e.getSource();
            if(dbgNb.getBoolean()) {
                System.err.println(MOD + "kit installed: "
                                + ep.getEditorKit().getClass().getSimpleName());
            }
            if(captureDefaultKeyTypedAction(ep)) {
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        Settings.touchValue(BaseKit.class,
                                            SettingsNames.CUSTOM_ACTION_LIST);
                    }
                });
            }
            
            // Make sure the nomadic editors have the right cursor.
            checkCaret(ep);
        }
    }
    
    private static void closeTC(JEditorPane ep, TopComponent tc) {
        ViManager.closeFile(ep, tc);
        editorToCaret.remove(ep);
    }
    
    private static boolean captureDefaultKeyTypedAction(JEditorPane ep) {
        boolean captured = false;
        
        EditorKit kit = ep.getEditorKit();
        Action a = kitToDefaultKeyAction.get(kit.getClass());
        if(a == null && kit instanceof BaseKit) {
            a = ((BaseKit)kit).getActionByName(BaseKit.defaultKeyTypedAction);
            if(!(a instanceof DefaultViFactory.EnqueCharAction)) {
                kitToDefaultKeyAction.put(kit.getClass(), a);
                if(dbgNb.getBoolean()) {
                    System.err.println(MOD + "capture:"
                                + " kit: " + kit.getClass().getSimpleName()
                                + " action: " + a.getClass().getSimpleName());
                }
                captured = true;
            }
        }
        return captured;
    }
    
    public static final void checkCaret(JEditorPane ep) {
        if(!(ep.getCaret() instanceof ViCaret)) {
            if(editorToCaret.get(ep) == null) {
                editorToCaret.put(ep, ep.getCaret());
                if(dbgNb.getBoolean()) {
                    System.err.println(MOD + "capture caret");
                }
            }
            NbFactory.installCaret(ep, new NbCaret());
        }
    }
    
    // This was private, but there are times when a TopComponent with
    // an editor pane sneaks through the TC open/activation logic (DiffExecuter)
    static void activateTC(JEditorPane ep, Object o, String tag) {
        TopComponent tc = (TopComponent)o;
        if(ep != null)
            checkCaret(ep);
        addEpToTC(tc, ep);
        ViManager.activateFile(ep, tc, tag);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Some methods to handle a TC's PROP_JEP.
    // Note that a TC may have more than one JEditorPane.
    //
    
    /** Add the editor as a property to the top component, if either parameter
     * is null, then false is returned.
     * @return true if editor added or already was there, false if couldn't add
     */
    static boolean addEpToTC(TopComponent tc, JEditorPane ep) {
        if(ep == null || tc == null)
            return false;
        @SuppressWarnings("unchecked")
        Set<JEditorPane> s = (Set<JEditorPane>)tc.getClientProperty(PROP_JEP);
        if(s == null) {
            s = new HashSet<JEditorPane>();
            tc.putClientProperty(PROP_JEP, s);
        }
        if(!s.contains(ep))
            s.add(ep);
        return true;
    }
    
    static JEditorPane fetchEpFromTC(TopComponent tc, JEditorPane ep) {
        if(fetchEpFromTC(tc).contains(ep))
            return ep;
        return null;
    }
    
    static Set<JEditorPane> fetchEpFromTC(TopComponent tc) {
        @SuppressWarnings("unchecked")
        Set<JEditorPane> s = (Set<JEditorPane>)tc.getClientProperty(PROP_JEP);
        if(s != null)
            return s;
        return Collections.emptySet();
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
                    // XXX doOptionsInitHack(); // HORROR STORY
                    // Do this for activate (but not for open)
                    ViManager.requestSwitch(ep);
                }
            } else if(evt.getPropertyName().equals(TopComponent.Registry.PROP_OPENED)) {
                // For each top component we know about, see if it is still
                // opened.
                // NEEDSWORK: checking each buffer (until NB6 PROP_TC_OPENED)
                @SuppressWarnings("unchecked")
                Set<TopComponent> newSet = (Set<TopComponent>)evt.getNewValue();
                @SuppressWarnings("unchecked")
                Set<TopComponent> oldSet = (Set<TopComponent>)evt.getOldValue();
                if(newSet.size() > oldSet.size()) {
                    // something OPENing
                    @SuppressWarnings("unchecked")
                    Set<TopComponent> s = (Set<TopComponent>)
                            ((HashSet<TopComponent>)newSet).clone();
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
                    @SuppressWarnings("unchecked")
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
                        for (JEditorPane ep : fetchEpFromTC(tc)) {
                            tcDumpInfo(tc, "close");
                            closeTC(ep, tc);
                        }
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
        EditorCookie ec = tc.getLookup().lookup(EditorCookie.class);
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
        EditorCookie ec = tc.getLookup().lookup(EditorCookie.class);
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
    
    public static void runInDispatch(boolean wait, Runnable runnable) {
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

    //////////////////////////////////////////////////////////////////////
    //
    // Tools > jVi menu enable checkbox
    //
    
    /** The action used for the NB system.
     * NEEDSWORK: want to plug the action into the checkbox
     *            but see icon comment in constructor.
     */
    private static class JViEnableAction extends CallableSystemAction {
        protected static final String NAME="jVi";
        private JCheckBoxMenuItem cb;

        private static class MyBox extends JCheckBoxMenuItem {
            MyBox() { super(NAME); }
            @Override
            public void addNotify() {
                super.addNotify();
                setSelected(jViEnabled);
            }
        }
        
        JViEnableAction() {
            super();
            putValue("noIconInMenu", Boolean.TRUE); // NOI18N

            cb = new MyBox();

            // Note if "this" is added with setAction, then icon
            cb.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doAction(cb.isSelected());
                }
            });
            /*cb.setSelected(true);*/
        }
        
        @Override
        public boolean isEnabled() {
            return true;
        }
        
        @Override
        public JMenuItem getMenuPresenter() {
            return cb;
        }
        
        boolean isSelected() {
            return cb.isSelected();
        }
        
        /** This is only to change the state of the checkbox,
         * there should be no other behavior here
         */
        void setSelected(boolean b) {
            cb.setSelected(b);
        }

        @Override
        public void performAction() {
            actionPerformed(null);
        }

        /** the system action toggles the current state */
        @Override
        public void actionPerformed(ActionEvent e) {
            boolean enable = !isSelected();
            setSelected(enable);
            doAction(enable);
        }
        
        protected void doAction(final boolean enabled) {
            runInDispatch(false, new Runnable() {
                public void run() {
                    if(enabled)
                        new RunJViEnable().run();
                    else
                        new RunJViDisable().run();
                }
            });
            getModulePreferences().putBoolean(PREF_ENABLED, enabled);
        }
        
        @Override
        public HelpCtx getHelpCtx() {
            return HelpCtx.DEFAULT_HELP;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        protected String iconResource() {
            return "/com/raelity/jvi/resources/jViLogo.png";
        }
        
    }

    //////////////////////////////////////////////////////////////////////
    //
    // :e# file name completion based on NB code completion
    //

    private static DocumentListener ceDocListen;
    private static boolean ceInSubstitute;
    private static BooleanOption dbgCompl;

    static void commandEntryAssist(ViCmdEntry cmdEntry, boolean enable) {
        if(dbgCompl == null)
            dbgCompl = (BooleanOption)Options.getOption(Options.dbgCompletion);
        JTextComponent ceText = cmdEntry.getTextComponent();
        if(!enable) {
            // Finished, make sure everything's shutdown
            Completion.get().hideAll();
            ceText.removeFocusListener(initShowCompletion);
            ceText.getDocument().removeDocumentListener(ceDocListen);
            ceDocListen = null;
            return;
        }

        if(!Options.getOption(Options.autoPopupFN).getBoolean())
            return;

        ceInSubstitute = false;

        Document ceDoc = ceText.getDocument();
        ceDocListen = new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
            }
            public void insertUpdate(DocumentEvent e) {
                ceDocCheck(e.getDocument());
            }
            public void removeUpdate(DocumentEvent e) {
                ceDocCheck(e.getDocument());
            }
        };
        ceDoc.addDocumentListener(ceDocListen);

        String text = null;
        try {
            text = ceText.getDocument()
                            .getText(0, ceText.getDocument().getLength());
        } catch(BadLocationException ex) {}

        // see if initial conditions warrent bringing up completion
        if(text.startsWith("e#")) {
            // Wait till combo's ready to go.
            if(ceText.hasFocus())
                initShowCompletion.focusGained(null);
            else
                ceText.addFocusListener(initShowCompletion);
        }
    }

    private static FocusListener initShowCompletion = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            if(dbgCompl.getBoolean())
                System.err.println("INIT SHOW:");
            Completion.get().showCompletion();
        }
    };

    private static void ceDocCheck(Document doc) {
        try {
            if(doc.getLength() == 2 && !ceInSubstitute) {
                if("e#".equals(doc.getText(0, doc.getLength()))) {
                    if(dbgCompl.getBoolean())
                        System.err.println("SHOW:");
                    Completion.get().showCompletion();
                }
            } else if(doc.getLength() < 2) {
                if(dbgCompl.getBoolean())
                    System.err.println("HIDE:");
                Completion.get().hideCompletion();
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    public static class ViCommandCompletionProvider
    implements CompletionProvider {
        /*public CompletionTask createTask(int queryType,
                                         JTextComponent jtc) {
            return new AsyncCompletionTask(
                    new ViCommandAsyncCompletionQuery(jtc), jtc);

        }*/

        public CompletionTask createTask(int queryType,
                                         JTextComponent jtc) {
            if (queryType != CompletionProvider.COMPLETION_QUERY_TYPE)
                return null;
            return new ViCommandCompletionTask(jtc);
        }

        public int getAutoQueryTypes(JTextComponent jtc,
                                     String typedText) {
            return 0;
        }
    }

    private static boolean filterDigit(String filter) {
        return filter.length() > 0 && Character.isDigit(filter.charAt(0));
    }

    // Some state info about the items.
    private final static int ITEM_SELECTED = 0x01;
    private final static int ITEM_MODIFIED = 0x02;

    public static class ViCommandCompletionTask implements CompletionTask {

        JTextComponent jtc;
        List<ViCommandCompletionItem> query;

        public ViCommandCompletionTask(JTextComponent jtc) {
            this.jtc = jtc;
        }
        
        public void query(CompletionResultSet resultSet) {
            query = new ArrayList<Module.ViCommandCompletionItem>();

            int i = 0;
            Object o;
            Font font = getTxtFont();
            while((o = ViManager.getTextBuffer(++i)) != null) {
                TopComponent tc = (TopComponent)o;
                int flags = 0;
                if(TopComponent.getRegistry().getActivated() == tc)
                    flags |= ITEM_SELECTED;

                ImageIcon icon = tc.getIcon() != null
                        ? new ImageIcon(tc.getIcon())
                        : null;

                Set<JEditorPane> jepSet = fetchEpFromTC(tc);
                String name = null;
                if(jepSet.size() == 1) {
                    for (JEditorPane jep : jepSet) {
                        Document doc = jep.getDocument();
                        if(NbEditorUtilities.getDataObject(doc).isModified())
                            flags |= ITEM_MODIFIED;
                        name = NbEditorUtilities.getFileObject(doc).getNameExt();
                    }
                }
                if(name == null)
                    name = ViManager.getViFactory().getDisplayFilename(o);
                query.add(new ViCommandCompletionItem(
                                name, String.format("%02d", i), icon,
                                false, flags, font,
                                2)); // offset 2 is after "e#"
            }
            genResults(resultSet, "QUERY");
        }

        public void refresh(CompletionResultSet resultSet) {
            genResults(resultSet, "REFRESH");
        }
        
        // getTxtFont taken from core/swing/tabcontrol/src/
        // org/netbeans/swing/tabcontrol/plaf/AbstractViewTabDisplayerUI.java
        private Font getTxtFont() {
            //font = UIManager.getFont("TextField.font");
            //Font font = UIManager.getFont("Tree.font");
            Font txtFont;
            txtFont = (Font) UIManager.get("windowTitleFont");
            if (txtFont == null) {
                txtFont = new Font("Dialog", Font.PLAIN, 11);
            } else if (txtFont.isBold()) {
                // don't use deriveFont() - see #49973 for details
                txtFont = new Font(txtFont.getName(),
                                   Font.PLAIN, txtFont.getSize());
            }
            return txtFont;
        }

        public void genResults(CompletionResultSet resultSet, String tag) {
            String dbsString = "";
            try {
                Document doc = jtc.getDocument();
                String text = doc.getText(0, doc.getLength());
                if(dbgCompl.getBoolean())
                    dbsString = tag + ": '" + text + "'";
                if(text.startsWith("e#")) {
                    int startOffset = 2; // char after 'e#'
                    // NEEDSWORK: can't count on the caret position since
                    // this is sometimes called from the event dispatch thread
                    // so just use the entire string. Set the caret to
                    // the end of the string.
                    //int caretOffset = jtc.getCaretPosition();
                    int caretOffset = text.length();
                    // skip white space
                    for(; startOffset < caretOffset; startOffset++)
                        if(!Character.isWhitespace(text.charAt(startOffset)))
                            break;
                    String filter = text.substring(startOffset, caretOffset);
                    if(dbgCompl.getBoolean())
                        dbsString += ", filter '" + filter + "'";
                    resultSet.setAnchorOffset(startOffset);
                    boolean fFilterDigit = filterDigit(filter);
                    for (ViCommandCompletionItem item : query) {
                        String checkItem = fFilterDigit ? item.num : item.name;
                        if(filter.regionMatches(true, 0, checkItem, 0,
                                                filter.length())) {
                            item.fFilterDigit = fFilterDigit;
                            resultSet.addItem(item);
                        }
                    }
                }
            } catch (BadLocationException ex) {
            }
            if(dbgCompl.getBoolean()) {
                dbsString += ", result: " + resultSet;
                System.err.println(dbsString);
            }
            resultSet.finish();
        }

        public void cancel() {
            if(dbgCompl.getBoolean())
                System.err.println("CANCEL:");
            Completion.get().hideAll();
        }
    }

    private static class ViCommandCompletionItem implements CompletionItem {
        private static String fieldColorCode = "0000B2";
        private static Color fieldColor = Color.decode("0x" + fieldColorCode);
        //private static Color fieldColor = Color.decode("0xC0C0B2");
        private Font font;
        private static ImageIcon fieldIcon = null;
        private ImageIcon icon;
        private String name;
        private String nameLabel; // with padding for wide icon
        private String num;
        private boolean fFilterDigit;
        private int startOffset;

        ViCommandCompletionItem(String name, String num, ImageIcon icon,
                                boolean fFilterDigit, int flags, Font font,
                                int dotOffset) {
            this.name = name;
            this.num = num;
            this.startOffset = dotOffset;
            this.icon = icon != null ? icon : fieldIcon;
            this.fFilterDigit = fFilterDigit;
            this.font = font;

                            // + "<font color=\"#000000\">"
            nameLabel = "<html>&nbsp;&nbsp;"
                            + ((flags & ITEM_SELECTED) != 0 ? "<b>" : "")
                            + ((flags & ITEM_MODIFIED) != 0
                                ? "<font color=\"#" + fieldColorCode + "\">"
                                : "<font color=\"#000000\">")
                            + name
                            + ((flags & ITEM_MODIFIED) != 0 ? " *" : "")
                            + ((flags & ITEM_SELECTED) != 0 ? "</b>" : "")
                            + "</font>"
                            + "</html>";
            
            //if(fieldIcon == null){
            //    fieldIcon = new ImageIcon(Utilities.loadImage(
            //            "org/netbeans/modules/textfiledictionary/icon.png"));
            //}
        }
        public void defaultAction(JTextComponent jtc) {
            if(dbgCompl.getBoolean())
                System.err.println("DEFAULT ACTION: '" + name + "'");
            try {
                ceInSubstitute = true;
                doSubstitute(jtc);
            } finally {
                ceInSubstitute = false;
            }
            Completion.get().hideAll();

            // Go for it
            Action act = jtc.getKeymap().getAction(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            if(act != null)
                act.actionPerformed(
                    new ActionEvent(jtc, ActionEvent.ACTION_PERFORMED, "\n"));
        }

        private void doSubstitute(JTextComponent jtc) {
            
            Document doc = jtc.getDocument();
            int caretOffset = doc.getLength(); // clear to end of line
            
            //String value = name;
            String value = num;
            
            try {
                doc.remove(startOffset, caretOffset - startOffset);
                doc.insertString(startOffset, value, null);
                jtc.setCaretPosition(startOffset + value.length());
            } catch (BadLocationException e) {
                ErrorManager.getDefault().notify(
                        ErrorManager.INFORMATIONAL, e);
            }
        }

        int hack = 0;
        public void processKeyEvent(KeyEvent evt) {
            if(dbgCompl.getBoolean()
               /*&& (evt.getKeyChar() != KeyEvent.CHAR_UNDEFINED
                        && evt.getID() == KeyEvent.KEY_TYPED
                    || evt.getKeyChar() == KeyEvent.CHAR_UNDEFINED)*/)
                System.err.println("ViCompletionItem: '" + name + "' "
                        + evt.paramString());
            if(evt.getID() == KeyEvent.KEY_PRESSED
                    && evt.getKeyChar() == KeyEvent.VK_TAB) {
                // The logic in CompletionImpl that does getInsertPrefix
                // sets caretOffset from selection start. So if "e#n" is
                // selected and tab is entered, then caretOffset gets set to 0
                // and anchorOffset is 2 (as always) this ends up with a "-2"
                // -2 used in a String.subsequence....
                // If TAB, get rid of selection and position at end of text
                JTextComponent jtc = (JTextComponent) evt.getSource();
                jtc.setCaretPosition(jtc.getDocument().getLength());
            }
            int i = 0;
            if(evt.getKeyCode() == KeyEvent.VK_DOWN
                    || evt.getKeyCode() == KeyEvent.VK_UP)
                hack++;
            if(evt.getID() == KeyEvent.KEY_TYPED
                    && evt.getKeyChar() == KeyEvent.VK_TAB) {
                evt.consume();
            }
        }

        public int getPreferredWidth(Graphics g, Font font) {
            return CompletionUtilities.getPreferredWidth(nameLabel, num,
                                                         g, font);
        }

        public void render(Graphics g, Font defaultFont,
                           Color defaultColor, Color backgroundColor,
                           int width, int height, boolean selected) {
            if(dbgCompl.getBoolean())
                System.err.println("RENDER: '" + name
                                   + "', selected " + selected);
            Font f = font == null ? defaultFont : font;
            Graphics2D g2 = (Graphics2D)g;
            renderingHints = pushCharHint(g2, renderingHints);
            CompletionUtilities.renderHtml(icon, nameLabel, num,
                                           g, f,
                                           selected ? Color.white : fieldColor,
                                           width, height, selected);
            popCharHint(g2, renderingHints);
        }

        private RenderingHints renderingHints;
        private boolean charHintsEnabled = true;
        private RenderingHints pushCharHint(Graphics2D g2,
                                              RenderingHints rh) {
            if(!charHintsEnabled)
                return null;
            
            if(rh != null)
                rh.clear();
            else
                rh = new RenderingHints(null);
            // hints from: "How can you improve Java Fonts?"
            // http://www.javalobby.org/java/forums/m92159650.html#92159650
            // read entire discussion, KEY_TEXT_ANTIALIASING shouldn't need change
            // NOTE: problem is that KEY_AA should not be on while doing text.
            rh.put(RenderingHints.KEY_ANTIALIASING,
                   g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
            rh.put(RenderingHints.KEY_TEXT_ANTIALIASING,
                   g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING));
            rh.put(RenderingHints.KEY_RENDERING,
                   g2.getRenderingHint(RenderingHints.KEY_RENDERING));

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_OFF);
            // g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            //                     RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                                RenderingHints.VALUE_RENDER_QUALITY);
            return rh;
        }

        private void popCharHint(Graphics2D g2, RenderingHints rh) {
            if(!charHintsEnabled || rh == null)
                return;
            g2.addRenderingHints(rh);
        }

        public CompletionTask createDocumentationTask() {
            return null;
        }

        public CompletionTask createToolTipTask() {
            return null;
        }

        public boolean instantSubstitution(JTextComponent component) {
            return false;
        }

        public int getSortPriority() {
            return 0;
        }

        public CharSequence getSortText() {
            return fFilterDigit ? num : name;
        }

        public CharSequence getInsertPrefix() {
            return fFilterDigit ? "" : name.toLowerCase();
        }

    }

    /*public static class ViCommandAsyncCompletionQuery
    extends AsyncCompletionQuery {
        JTextComponent jtc;
        ViCommandCompletionTask ct;

        public ViCommandAsyncCompletionQuery(JTextComponent jtc) {
            super();
            this.jtc = jtc;
            System.err.println("ASYNC SETUP");
        }

        protected void query(CompletionResultSet resultSet,
                             Document doc,
                             int caretOffset) {
            assert jtc.getDocument() == doc;
            if(ct == null)
                ct = new ViCommandCompletionTask(jtc);
            ct.query(resultSet);
        }

        @Override
        protected boolean canFilter(JTextComponent component) {
            //return super.canFilter(component);
            return true;
        }

        @Override
        protected void filter(CompletionResultSet resultSet) {
            //super.filter(resultSet);
            ct.refresh(resultSet);
        }
    }*/
            
            /*names.add("one");
            names.add("two");
            names.add("three");
            names.add("four");
            names.add("five");
            names.add("six");
            names.add("seven");
            names.add("eight");
            names.add("nine");
            names.add("ten");
            names.add("eleven");
            names.add("twelve");
            names.add("thirteen");
            names.add("fourteen");
            names.add("fifteen");
            names.add("sixteen");
            names.add("seventeen");
            names.add("eighteen");
            names.add("nineteen");
            names.add("thirty");

            nums.add(1);
            nums.add(2);
            nums.add(3);
            nums.add(4);
            nums.add(5);
            nums.add(6);
            nums.add(7);
            nums.add(8);
            nums.add(9);
            nums.add(10);
            nums.add(11);
            nums.add(12);
            nums.add(13);
            nums.add(14);
            nums.add(15);
            nums.add(16);
            nums.add(17);
            nums.add(18);
            nums.add(19);
            nums.add(30);
            genResults(resultSet, "QUERY");*/
}
