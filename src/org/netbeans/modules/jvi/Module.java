package org.netbeans.modules.jvi;

import com.raelity.jvi.options.BooleanOption;
import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Options;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.ViCmdEntry;
import com.raelity.jvi.ViInitialization;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.options.OptUtil;
import com.raelity.jvi.swing.CommandLine;
import com.raelity.jvi.swing.SwingFactory;
import com.raelity.jvi.swing.KeyBinding;
import com.raelity.jvi.ViCaret;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.Action;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.TextAction;
import javax.swing.UIManager;

import javax.swing.text.EditorKit;
import javax.swing.text.Keymap;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.MultiKeyBinding;
import org.netbeans.editor.BaseAction;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.editor.settings.storage.spi.StorageFilter;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.InstanceCookie;
import org.openide.ErrorManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.modules.ModuleInfo;
import org.openide.modules.ModuleInstall;
import org.openide.modules.SpecificationVersion;
import org.openide.util.actions.SystemAction;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
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
public class Module extends ModuleInstall
{
    private static Logger LOG = Logger.getLogger(Module.class.getName());

    private static NbFactory factory;

    private static boolean jViEnabled;

    private static final String MOD
            = "Module-" + Integer.toHexString(System.identityHashCode(
                            Module.class.getClassLoader())) + ": ";

    private static final String PREF_ENABLED = "viEnabled";
    
    // The persistent option names and their variables
    public static final String DBG_MODULE = "DebugNbModule";
    public static final String DBG_TC = "DebugNbTopComponent";
    public static final String DBG_HL = "DebugNbHilight";
    static BooleanOption dbgNb;
    static BooleanOption dbgAct;
    static BooleanOption dbgHL;

    public static final String HACK_CC = "NB6.7 Code Completion";
    public static final String HACK_SCROLL = "NB6.7 Text Scroll";
    
    private static TopComponentRegistryListener topComponentRegistryListener;
    private static KeybindingsInjector KB_INJECTOR = null;
    
    private static final String JVI_INSTALL_ACTION_NAME = "jvi-install";
    private static Map<JEditorPane, Action> epToDefaultKeyAction
            = new HashMap<JEditorPane, Action>();
    private static Map<EditorKit, Action> kitToDefaultKeyAction
            = new HashMap<EditorKit, Action>();
    
    private static Map<JEditorPane, Caret> editorToCaret
            = new WeakHashMap<JEditorPane, Caret>();

    // a SET
    private static Map<TopComponent, Object> tcChecked
            = new WeakHashMap<TopComponent, Object>();

    // a SET
    private static Map<JEditorPane, Object> knownEditors
            = new WeakHashMap<JEditorPane, Object>();

    private static Runnable shutdownHook;

    @ServiceProvider(service=ViInitialization.class)
    public static class Init implements ViInitialization
    {
      public void init()
      {
        Module.init();
      }
    }

    private static void init()
    {
        PropertyChangeListener pcl = new PropertyChangeListener()
        {
            public void propertyChange(PropertyChangeEvent evt)
            {
                String pname = evt.getPropertyName();
                if(pname.equals(ViManager.P_BOOT)) {
                    addDebugOptions();
                } else if(pname.equals(ViManager.P_LATE_INIT)) {
                    NbColonCommands.init();
                    addDebugColonCommands();
                }
            }
        };
        ViManager.addPropertyChangeListener(ViManager.P_BOOT, pcl);
        ViManager.addPropertyChangeListener(ViManager.P_LATE_INIT, pcl);
    }

    public static String cid(Object o)
    {
        if (o == null)
            return "(null)";
        return o.getClass().getSimpleName() + "@" + id(o);
    }

    public static String id(Object o)
    {
        if (o == null)
            return "(null)";
        return Integer.toHexString(System.identityHashCode(o));
    }

    static boolean jViEnabled() {
        return jViEnabled;
    }

    static boolean isDbgNb() {
        return dbgNb != null && dbgNb.getBoolean();
    }

    /** @return the module specific preferences.
     */
    private static Preferences getModulePreferences() {
        return factory.getPreferences().node("module");
    }
    private static boolean isModuleEnabled() {
        Preferences prefs = getModulePreferences();
        return prefs.getBoolean(PREF_ENABLED, true);
    }
    private static void setModuleEnabled(boolean flag) {
        getModulePreferences().putBoolean(PREF_ENABLED, flag);
    }

    /** @return false when editor already in there */
    private static boolean addKnownEditor(JEditorPane ep) {
        return knownEditors.put(ep, null) != null;
    }

    /** @return false when editor already was not in there */
    private static boolean removeKnownEditor(JEditorPane ep) {
        editorToCaret.remove(ep);
        return knownEditors.remove(ep) != null;
    }
    
    /** called when the module is loaded (at netbeans startup time) */
    @Override
    public void restored() {
        if (isDbgNb()) {
            System.err.println(MOD + "***** restored *****");
        }

        for (ModuleInfo mi : Lookup.getDefault().lookupAll(ModuleInfo.class)) {
            if (mi.getCodeNameBase().equals(
                    "org.netbeans.modules.editor.codetemplates")) {
                if (mi.getSpecificationVersion().compareTo(
                        new SpecificationVersion("1.8.0")) < 0) {
                    ViManager.putHackMap(
                            "NB-codetemplatesHang", Boolean.TRUE);
                }
            } else if (mi.getCodeNameBase().equals(
                    "org.netbeans.modules.editor.lib2")) {
                //System.err.println("LIB2 VERSION: "
                //        + mi.getSpecificationVersion());
                if (mi.getSpecificationVersion().compareTo(
                        new SpecificationVersion("1.11.1.2")) >= 0) {
                    ViManager.putHackMap(HACK_CC, Boolean.TRUE);
                    ViManager.putHackMap(HACK_SCROLL, Boolean.TRUE);
                }
            }
        }

        earlyInit(); // set up the ViFactory

        // in layer.xml Actions/Tools: <file name="o-n-m-jvi-enable.instance">
        // produces the checkbox linked to preferences.
        Preferences prefNode = getModulePreferences();
        if(prefNode.get(PREF_ENABLED, "").equals("")) {
            // Not set, so set it to true
            prefNode.putBoolean(PREF_ENABLED, true);
        }

        // Monitor activations/opens/closes.
        if(topComponentRegistryListener == null) {
            topComponentRegistryListener = new TopComponentRegistryListener();
            TopComponent.getRegistry().addPropertyChangeListener(
                    topComponentRegistryListener);
        }

        if (isModuleEnabled()) {
            runInDispatch(true, new RunJViEnable());
        }

        prefNode.addPreferenceChangeListener(new PreferenceChangeListener() {
            public void preferenceChange(PreferenceChangeEvent evt) {
                System.err.println("PREF CHANGE: " + evt);
                if(evt.getKey().equals(PREF_ENABLED)) {
                    System.err.println("PREF CHANGE TO: " + evt.getNewValue());
                    boolean enabled = getModulePreferences()
                            .getBoolean(PREF_ENABLED, true);
                    EventQueue.invokeLater(enabled
                                           ? new RunJViEnable()
                                           : new RunJViDisable());
                }
            }
        });
    }

    static void setShutdownHook(Runnable hook) {
        shutdownHook = hook;
    }

    @Override
    public void close() {
        if(shutdownHook != null)
            shutdownHook.run();
    }

    private static boolean didEarlyInit = false;
    private static synchronized void earlyInit() {
        if(didEarlyInit)
            return;
        didEarlyInit = true;

        runInDispatch(true, new Runnable() {
            public void run() {
                factory = new NbFactory();
                ViManager.setViFactory(factory);
            }
        });
    }

    private static class RunJViEnable implements Runnable {
        public void run() {
            if(jViEnabled)
                return;
            jViEnabled = true;

            if(isDbgNb())
                System.err.println(MOD + " runJViEnable knownJEP: "
                        + knownEditors.size());

            updateKeymap();

            // give all the editors the jVi DKTA and cursor
            for (JEditorPane ep : knownEditors.keySet()) {
                captureDefaultKeyTypedActionAndEtc(ep);
                checkCaret(ep);
            }
        }
    }

    /**
     * Restore editor's caret's and keymaps
     */
    private static class RunJViDisable implements Runnable {
        public void run() {
            if(!jViEnabled)
                return;
            jViEnabled = false;

            if(isDbgNb())
                System.err.println(MOD + " runJViDisable knownJEP: "
                        + knownEditors.size());

            // restore the carets
            for (JEditorPane ep : knownEditors.keySet()) {
                Caret c01 = editorToCaret.get(ep);
                if(c01 != null) {
                    if(ep.getCaret() instanceof NbCaret) {
                        NbFactory.installCaret(ep, c01);
                        if(isDbgNb()) {
                            System.err.println("restore caret: "
                                    + factory.getFS().getDisplayFileName(
                                            factory.getAppView(ep)));
                        }
                    }
                    editorToCaret.remove(ep);
                }
            }
            if(editorToCaret.size() > 0) {
                System.err.println(MOD + "restore caret: "
                    + "HUH? editorToCaret size: " + editorToCaret.size());
            }

            if(isDbgNb())
                ViManager.dump(System.err);

            JViOptionWarning.clear();
            updateKeymap();
        }
    }
    
    private static void addDebugOptions()
    {
        dbgNb = OptUtil.createBooleanOption(DBG_MODULE, false);
        OptUtil.setupOptionDesc(DBG_MODULE, "Module interface",
                                "Module and editor kit install/install");

        dbgAct = OptUtil.createBooleanOption(DBG_TC, false);
        OptUtil.setupOptionDesc(DBG_TC, "Top Component",
                                "TopComponent activation/open");

        dbgHL = OptUtil.createBooleanOption(DBG_HL, false);
        OptUtil.setupOptionDesc(DBG_HL, "Hilighting",
                                "Visual/Search highlighting");
    }
    
    private static void addDebugColonCommands() {
        
        //
        // Some debug commands
        //
        ColonCommands.register("topcomponentDump", "topcomponentDump",
            new ActionListener() {
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
            }
        );
        ColonCommands.register("kitDump", "kitDump",
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ViOutputStream os = ViManager.createOutputStream(
                            null, ViOutputStream.OUTPUT, "Known Kits");
                    for (Map.Entry<EditorKit, Action> entry
                            : kitToDefaultKeyAction.entrySet()) {
                        os.println(String.format("%20s %s",
                                   entry.getKey().getClass().getSimpleName(),
                                   entry.getValue().getClass().getSimpleName()));
                    }
                    os.close();
                }
            }
        );
        ColonCommands.register("checkFsActList", "checkFsActList",
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    for (String act : FsAct.getFsActList()) {
                        if(fetchFileSystemAction(act) == null) {
                            System.err.println("Not found: " + act);
                        }
                    }

                }
            }
        );
        
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
        FileObject fo = FileUtil.getConfigFile(path);
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
    
    private static void updateKeymap() {
        if (KB_INJECTOR != null) {
            if(isDbgNb())
                System.err.println("Injector: updateKeymap: ");
            KB_INJECTOR.forceKeymapRefresh();
        } // else no keymap has been loaded yet
    }
    
    // "kit-install-action-name-list" setting value factory
    public static List<String> getKitInstallActionNameList() {
        List<String> l;
        l = Collections.singletonList(JVI_INSTALL_ACTION_NAME);
        return l;
    }

    // invoked by filesytem for adding actions to "Editor/Actions"
    public static Action createKBA(Map params) {
        return KeyBinding.getAction((String)params.get("action-name"));
    }

    //
    // registered in META-INF/services
    //
    // http://www.netbeans.org/issues/show_bug.cgi?id=90403
    //
    public static final class KeybindingsInjector
    extends StorageFilter<Collection<KeyStroke>, MultiKeyBinding>
    implements PropertyChangeListener
    {
        private static final MultiKeyBinding KP_UP = new MultiKeyBinding(
                KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0), "ViUpKey");
        private static final MultiKeyBinding KP_DOWN = new MultiKeyBinding(
                KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, 0), "ViDownKey");

        // rebuilt/updated when the jvi bindings change
        private static final Map<Collection<KeyStroke>, MultiKeyBinding> mapJvi
                = new HashMap<Collection<KeyStroke>, MultiKeyBinding>();

        Map<String, Map<Collection<KeyStroke>, MultiKeyBinding> > origMaps
            = new HashMap<String,Map<Collection<KeyStroke>,MultiKeyBinding> >();

        public KeybindingsInjector() {
            super("Keybindings");
            KB_INJECTOR = this;
            earlyInit();
            KeyBinding.addPropertyChangeListener(KeyBinding.KEY_BINDINGS, this);
            if(isDbgNb())
                System.err.println("~~~ KeybindingsInjector: " + this);
        }

        public void propertyChange(PropertyChangeEvent evt) {
            if(isDbgNb())
                System.err.println("Injector: change: "+evt.getPropertyName());
            if(evt.getPropertyName().equals(KeyBinding.KEY_BINDINGS)) {
                // the bindings have changed
                forceKeymapRefresh();
            }
        }

        void forceKeymapRefresh() {
            if(isDbgNb())
                System.err.println("Injector: forceKeymapRefresh: ");
            synchronized(mapJvi) {
                mapJvi.clear();
            }
            notifyChanges();
        }

        private String createKey(MimePath mimePath,
                                        String profile,
                                        boolean defaults) {
            String key = mimePath.toString()
                         + ":" + profile
                         + ":" + String.valueOf(defaults);
            return key;
        }

        private void checkBinding(String tag,
                Map<Collection<KeyStroke>, MultiKeyBinding> map) {
            for(MultiKeyBinding mkb : map.values()) {
                String match = "";
                if("caret-up".equals(mkb.getActionName())) {
                    match += "ACTION-MATCH: ";
                }
                if(mkb.getKeyStroke(0)
                        .equals(KeyStroke.getKeyStroke(KeyEvent.VK_UP,0))
                 ||mkb.getKeyStroke(0)
                        .equals(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP,0))) {
                    match += "KEY-MATCH: ";
                }
                if(!match.equals("")) {
                    System.err.println("UP-BINDING: " + tag + ": "
                            + match
                            + "key: " + mkb.getKeyStroke(0) + " "
                            + "action: " + mkb.getActionName());
                }
            }
        }
        
        @Override
        public void afterLoad(Map<Collection<KeyStroke>, MultiKeyBinding> map,
                              MimePath mimePath,
                              String profile,
                              boolean defaults) throws IOException {

            if (!jViEnabled())
                return;
            
            String key = createKey(mimePath, profile, defaults);
            Map<Collection<KeyStroke>, MultiKeyBinding> mapOrig
                    = origMaps.get(key);
            if(mapOrig == null)
                    mapOrig = new HashMap<Collection<KeyStroke>,
                                          MultiKeyBinding>();
            else
                mapOrig.clear(); // NEEDSWORK: is this the right thing?
            origMaps.put(key, mapOrig);

            // jVi binds VK_UP/DOWN, it doesn't bind VK_KP_UP/DOWN (but they
            // work as they should). However, NB code completion searches for
            // "caret-up" action and uses its keybindings for up in the code
            // completion list. CodeCompletion doesn't find VK_UP, it does find
            // VK_KP_UP so it binds that and VK_UP doesn't work in code
            // completion list. If code completion doesn't find anything, it
            // binds to its default, which is VK_UP. So here we also bind to
            // VK_KP_UP, so CC finds no bindings for "caret-up" and defaults
            // to VK_UP.(This means that VK_KP_UP won't work, sigh.)
            // If interested see editor.completion/src/org/netbeans/modules
            //                      /editor/completion/CompletionScrollPane

            synchronized(mapJvi) {
                if(mapJvi.size() == 0) { // If needed, build jvi bindings map.
                    List<JTextComponent.KeyBinding> l
                            = KeyBinding.getBindingsList();
                    for(JTextComponent.KeyBinding kb : l) {
                        MultiKeyBinding mkb
                                = new MultiKeyBinding(kb.key, kb.actionName);
                        mapJvi.put(mkb.getKeyStrokeList(), mkb);
                    }
                    // NEEDSWORK: cleanup
                    mapJvi.put(KP_UP.getKeyStrokeList(), KP_UP);
                    mapJvi.put(KP_DOWN.getKeyStrokeList(), KP_DOWN);
                    if(isDbgNb())
                        checkBinding("mapJvi", mapJvi);
                    if(isDbgNb())
                        System.err.println("Injector: build jVi map. size "
                                + mapJvi.size());
                }
                if(isDbgNb())
                    checkBinding("mapOrig", map);

                // Check each NB keybinding, if the first key
                // (of either mulit or single key binding) is
                // a jvi key, then save the NB binding for use
                // in beforeSave and remove it from map.
                List<KeyStroke> ksl = new ArrayList<KeyStroke>();
                ksl.add(null);
                Iterator<MultiKeyBinding> it = map.values().iterator();
                while(it.hasNext()) {
                    MultiKeyBinding mkbOrig = it.next();
                    // ksl is a keyStrokList of the first key of the NB binding
                    ksl.set(0, mkbOrig.getKeyStroke(0));
                    // If the NB binding starts with a jVi binding, then stash it
                    if(mapJvi.get(ksl) != null) {
                        mapOrig.put(mkbOrig.getKeyStrokeList(), mkbOrig);
                        it.remove();
                    }
                }

                if(isDbgNb())
                    System.err.println("Injector: afterLoad: "
                            +"mimePath '"+mimePath
                            +"' profile '"+profile
                            +"' defaults '" + defaults
                            +"' orig map size: " + map.size());

                map.putAll(mapJvi);
            }
            //hackCaptureCheckLater(); // check all opened TC's ep's.
        }

        @Override
        public void beforeSave(Map<Collection<KeyStroke>, MultiKeyBinding> map,
                               MimePath mimePath,
                               String profile,
                               boolean defaults) throws IOException {

            // NEEDSWORK: don't think this is correct
            //              consider if jvi is disabled afterLoad and beforeSave
            if(!jViEnabled())
                return;

            Map<Collection<KeyStroke>, MultiKeyBinding> mapOrig = 
                    origMaps.get(createKey(mimePath, profile, defaults));

            synchronized(mapJvi) {
                //
                // NEEDSWORK: the map doesn't have the jvi keybindings YET
                //
                // for(MultiKeyBinding kb : mapJvi.values()) {
                //     MultiKeyBinding curKb = map.get(kb.getkeyStrokeList());
                //     if(curKb == null)
                //     if(!curKb.getActionName().equals(kb.getActionname()))
                //     // assert curKb != null : "lost binding";
                //     // assert curkb.getActionName().equals(kb.getActionName())
                //     //                         : "changed";
                //     map.remove(curKb.getKeystrokeList());
                // }
            }
            if(mapOrig != null)
                map.putAll(mapOrig);

            if(isDbgNb())
                System.err.println("Injector: beforeSave: "
                        +"mimePath '"+mimePath
                        +"' profile '"+profile
                        +"' defaults '" + defaults
                        +"' orig map size: " + map.size());

            // ARE THERE ISSUES AROUND THE ORIGINAL DEFAULT KEYMAP???

        }
    } // End of KeybindingsInjector class

    // public because of the layer registration
    public static class JViInstallAction extends TextAction {
        public JViInstallAction() {
            super(JVI_INSTALL_ACTION_NAME);
            putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        }
        
        public void actionPerformed(ActionEvent e) {
            addKnownEditor((JEditorPane)e.getSource());
            if(!jViEnabled())
                return;
            JEditorPane ep = (JEditorPane)e.getSource();
            if(isDbgNb()) {
                System.err.printf(MOD + "kit installed: %s into %s\n",
                                ep.getEditorKit().getClass().getSimpleName(),
                                cid(ep));
            }
            // this is done before it is installed into the top component
            
            captureDefaultKeyTypedActionAndEtc(ep);
            
            // Make sure the nomadic editors have the right cursor.
            checkCaret(ep);
        }
    }
    
    /**
     * Get the defaultKeyTypedAction and other per ep stuff.
     * Do JViOptionWarning.
     *
     * The DKTA is captured on a per EditorKit basis. See
     *  Bug 140201 -  kit install action, JEditorPane, DefaultKeyTypedAction issues
     * for further info.
     * @param ep
     */
    private static void captureDefaultKeyTypedActionAndEtc(JEditorPane ep) {
        JViOptionWarning.monitorMimeType(ep);
        Action a = ep.getKeymap().getDefaultAction();
        EditorKit kit = ep.getEditorKit();
        if(!(a instanceof SwingFactory.EnqueCharAction)) {
            kitToDefaultKeyAction.put(kit, a);
            //putDefaultKeyAction(ep, a);

            ep.getKeymap().setDefaultAction(
                    factory.createCharAction(
                    DefaultEditorKit.defaultKeyTypedAction));

            if(isDbgNb()) {
                System.err.println(MOD + "capture: "
                        + cid(ep.getEditorKit()) + " " + cid(ep)
                        + " action: " + a.getClass().getSimpleName());
            }
        }
        else if(isDbgNb()) {
            System.err.println(MOD + "ALREADY DKTA: "
                    + cid(ep)
                    + " action: " + a.getClass().getSimpleName());
        }
    }

    /**
     * Find NB's DefaultKeyTypedAction for the edtior pane, or one for
     * the associted editor kit if the JEP doesn't have one.
     *
     * Turns out only need to save per EditorKit
     *
     * @param ep the edtior pane.
     * @return a best guess defaultKeytypedAction.
     */
    static final Action getDefaultKeyAction(JEditorPane ep) {
        Action a = epToDefaultKeyAction.get(ep);
        if(a == null) {
            EditorKit kit = ep.getEditorKit();
            a = kitToDefaultKeyAction.get(kit);
        }
        return a;
    }

    private static final void putDefaultKeyAction(JEditorPane ep, Action a) {
        epToDefaultKeyAction.put(ep, a);

        // Sometimes the ep when install action has the wrong default KTA
        // so kleep track per kit as well
        EditorKit kit = ep.getEditorKit();
        if(kitToDefaultKeyAction.get(kit) == null) {
            kitToDefaultKeyAction.put(kit, a);
        }
    }
    
    public static final void checkCaret(JEditorPane ep) {
        assert knownEditors.containsKey(ep);

        if(!jViEnabled())
            return;
        if(!(ep.getCaret() instanceof ViCaret)) {
            if(editorToCaret.get(ep) == null) {
                editorToCaret.put(ep, ep.getCaret());
                if(isDbgNb()) {
                    System.err.println(MOD + "capture caret");
                }
            }
            factory.setupCaret(ep);
        }
    }

    private static void closeTC(JEditorPane ep, TopComponent tc) {
        ViManager.closeAppView(fetchAvFromTC(tc, ep));
        // NEEDSWORK: spin through viman lists close any special
        // Can't do this. Just because a TC is closed, doesn't mean
        // that the associated editor won't be re-used, eg CodeEvaluator
        //removeKnownEditor(ep);
    }

    private static void deactivateTC(TopComponent tc) {
        if(tc == null)
            return;
        NbAppView av = null;
        for (NbAppView _av : fetchAvFromTC(tc)) {
            av = _av;
        }
        if(av == null)
            return;

        // NEEDSWORK: pick the right appview to deactivate
        // probably only allow one to be the "master"?
        ViManager.deactivateCurrentAppView(av);
    }
    
    // This was private, but there are times when a TopComponent with
    // an editor pane sneaks through the TC open/activation logic (DiffExecuter)
    private static void activateTC(JEditorPane ep, TopComponent tc, String tag) {
        if(ep != null)
            checkCaret(ep);
        NbAppView av = fetchAvFromTC(tc, ep);
        ViManager.activateAppView(av, tag);
    }

    private static void registerTC(NbAppView av, String tag) {
        ViManager.registerAppView(av, tag);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Some methods to handle a TC's PROP_JEP.
    // Note that a TC may have more than one JEditorPane.
    //

    static NbAppView fetchAvFromTC(TopComponent tc, JEditorPane ep) {
        if(tc == null)
            return (NbAppView)ep.getClientProperty(SwingFactory.PROP_AV);
        for (NbAppView av : fetchAvFromTC(tc)) {
            if(av.getEditor() == ep)
                return av;

        }
        return null;
    }

    /**
     * Look for a top component that this editor is contained in.
     * Only consider top components that have been opened.
     * @param editorPane
     * @return
     */
    static TopComponent getKnownTopComponent(JEditorPane editorPane)
    {
        Set<TopComponent> setTC = TopComponent.getRegistry().getOpened();
        TopComponent tc = null;
        Container parent = SwingUtilities
                .getAncestorOfClass(TopComponent.class, editorPane);
        while (parent != null) {
            tc = (TopComponent)parent;
            if(setTC.contains(tc))
                break;
            parent = SwingUtilities.getAncestorOfClass(TopComponent.class, tc);
        }
        return tc;
    }

    static Set<NbAppView> fetchAvFromTC(TopComponent tc) {
        @SuppressWarnings("unchecked")
        Set<NbAppView> s = (Set<NbAppView>)tc
                .getClientProperty(SwingFactory.PROP_AV);
        if(s != null)
            return s;
        return Collections.emptySet();
    }
    
    /** This class monitors the TopComponent registry and issues
     * <ul>
     * <li> ViManager.activateAppView("debuginfo", tc) </li>
     * <li> ViManager.deactivateCurrentAppView(ep, tc) </li>
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
            if(evt.getPropertyName()
                    .equals(TopComponent.Registry.PROP_ACTIVATED)) {
                TopComponent tc = (TopComponent) evt.getOldValue();
                if(tc != null) {
                    tcDumpInfo(evt.getOldValue(), "activated oldTC");

                    deactivateTC((TopComponent)evt.getOldValue());
                }

                //
                // NEEDSWORK: P_ACTV and switchto which of multiple av's
                // maybe if multiple, don't do an activate/switch
                //

                tc = (TopComponent) evt.getNewValue();
                if(tc != null) {
                    pokeTC(tc, true);

                    if(!tcChecked.containsKey(tc)) {
                        List<JEditorPane> l = getDescendentJep(tc);
                        for (JEditorPane ep : l) {
                            NbAppView av = NbAppView.updateAppViewForTC(tc, ep);
                            registerTC(av, "P_ACTV");
                        }
                        tcChecked.put(tc, null);
                    }

                    tcDumpInfo(tc, "activated newTC");

                    // grab the first editor in the top component
                    NbAppView av = null;
                    for (NbAppView _av : fetchAvFromTC(tc)) {
                        av = _av;
                    }

                    if(av != null) {
                        activateTC(av.getEditor(), tc, "P_ACTV");
                        ViManager.requestSwitch(av.getEditor());
                        doRunAfterActivateSwitch();
                    }
                }
            } else if(evt.getPropertyName()
                    .equals(TopComponent.Registry.PROP_TC_OPENED)) {
                TopComponent tc = (TopComponent) evt.getNewValue();

                boolean isEditor = pokeTC(tc, false);
                tcDumpInfo(tc, "open");

                boolean didRegister = false;
                List<JEditorPane> l = getDescendentJep(tc);
                for (JEditorPane ep : l) {
                    // if it is not an editor then treat it like a nomad
                    NbAppView av = NbAppView.updateAppViewForTC(
                            tc, ep, !isEditor);
                    registerTC(av, "P_OPEN");
                    didRegister = true;
                }
                if(isEditor && !didRegister) {
                    NbAppView av = NbAppView.updateAppViewForTC(tc, null);
                    registerTC(av, "P_OPEN_LAZY");
                }
            } else if(evt.getPropertyName()
                    .equals(TopComponent.Registry.PROP_TC_CLOSED)) {
                TopComponent tc = (TopComponent) evt.getNewValue();
                for (NbAppView av : fetchAvFromTC(tc)) {
                    tcDumpInfo(tc, "close");
                    closeTC(av.getEditor(), tc);
                }
            }
        }
    }

    static List<JEditorPane> getDescendentJep(Component parent) {
        return getDescendentJep(parent, true);
    }
    static List<JEditorPane> getDescendentJep(Component parent, boolean verb)
    {
        List<JEditorPane> l = new ArrayList<JEditorPane>(2);
        if (parent instanceof Container) {
            Component components[] = ((Container)parent).getComponents();
            for (int i = 0 ; i < components.length ; i++) {
                Component comp = components[i];
                if (comp != null) {
                    if(comp instanceof JEditorPane) {
                        l.add((JEditorPane)comp);
                        if(G.dbgEditorActivation.getBoolean()) {
                            JTextComponent ep = (JTextComponent)comp;
                            FileObject fo = null;
                            Document doc = ep.getDocument();
                            fo = NbEditorUtilities.getFileObject(doc);
                        }
                    } else if (comp instanceof Container) {
                        l.addAll(getDescendentJep(comp));
                    }
                }
            }
        }
        return l;
    }
    
    private static String ancestorStringTC(Object o)
    {
        StringBuilder s = new StringBuilder();
        TopComponent tc = null;
        TopComponent parent = (TopComponent)SwingUtilities
                .getAncestorOfClass(TopComponent.class, (Component)o);
        while (parent != null) {
            s.append(" ")
                .append(parent.getDisplayName())
                .append(":")
                .append(cid(parent));
            tc = parent;
            parent = (TopComponent)SwingUtilities.getAncestorOfClass(TopComponent.class, tc);
        }
        return s.toString();
    }
    
    private static final void tcDumpInfo(Object o, String tag)
    {
        if(!G.dbgEditorActivation.getBoolean())
            return;
        if(!(o instanceof TopComponent))
            return;
        TopComponent tc = (TopComponent) o;
        List<JEditorPane> panes = getDescendentJep(tc);
        Mode mode = WindowManager.getDefault().findMode(tc);
        if(dbgAct.getBoolean()) {
            System.err.format("trackTC: %s: %s:%s '%s' : nPanes = %d\n",
                    tag,
                    tc.getDisplayName(), cid(tc),
                    (mode == null ? "null" : mode.getName()),
                    panes.size());
        }
        if(dbgAct.getBoolean()) {
            for (JEditorPane ep : panes) {
                NbAppView av = fetchAvFromTC(tc, ep);
                if(av == null) {
                    System.err.printf("\tep %s tc: %s isEditable %b\n",
                            cid(ep), ancestorStringTC(ep), ep.isEditable());
                } else {
                    System.err.printf("\tep:%d %s tc: %s isEditable %b\n",
                            av.getWNum(),
                            cid(ep), ancestorStringTC(ep), ep.isEditable());
                }
            }
        }
    }

    private static boolean pokeTC(TopComponent tc, boolean force)
    {
        if(tc == null)
            return false;
        Lookup lookup = tc.getLookup();
        if(lookup == null)
            return false;
        EditorCookie ec = lookup.lookup(EditorCookie.class);
        if(ec == null)
            return false; // no editor cookie

        JEditorPane[] panes;
        if(force)
            panes = ec.getOpenedPanes(); // force the panes to be instantiated
        return true; // this top component has an editor cookie
    }
    

    static void runAfterActivateSwitch(Runnable runnable) {
    }

    static private void doRunAfterActivateSwitch() {
    }
    
    public static void runInDispatch(boolean wait, Runnable runnable) {
        if(EventQueue.isDispatchThread()) {
            runnable.run();
        } else if(!wait) {
            EventQueue.invokeLater(runnable);
        } else {
            Exception ex1 = null;
            try {
                EventQueue.invokeAndWait(runnable);
            } catch (InterruptedException ex) {
                ex1 = ex;
            } catch (InvocationTargetException ex) {
                ex1 = ex;
            }
            if(ex1 != null)
                LOG.log(Level.SEVERE, null, ex1);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // :e# file name completion based on NB code completion
    //

    static boolean EditorRegistryRegister(JTextComponent jtc) {
        boolean done = false;
        Exception ex1 = null;

        try {
            Class c = Lookup.getDefault().lookup(ClassLoader.class).loadClass(
                            "org.netbeans.modules.editor.lib2"
                            + ".EditorApiPackageAccessor");
            Method get = c.getMethod("get");
            Object o = get.invoke(null);
            Method register = c.getMethod("register", JTextComponent.class);
            register.invoke(o, jtc);

/*
            Boolean b = (Boolean) ViManager.HackMap.get(HACK_CC);
            if(b != null && b) {
                c = ((ClassLoader)(Lookup.getDefault()
                            .lookup(ClassLoader.class))).loadClass(
                                "org.netbeans.api.editor"
                                + ".EditorRegistry$Item");
                o = jtc.getClientProperty(c);
                if(o != null) {
                    Field field = c.getDeclaredField("ignoreAncestorChange");
                    field.setAccessible(true);
                    field.setBoolean(o, true);
                }
            }
*/
            done = true;
        } catch(ClassNotFoundException ex) {
            ex1 = ex;
        } catch(InvocationTargetException ex) {
            ex1 = ex;
        } catch(NoSuchMethodException ex) {
            ex1 = ex;
        } catch(IllegalAccessException ex) {
            ex1 = ex;
//      } catch (NoSuchFieldException ex) {
//          ex1 = ex;
        }
        if(ex1 != null)
            LOG.log(Level.SEVERE, null, ex1);

        return done;
    }

    private static CodeComplDocListener ceDocListen;
    private static boolean ceInSubstitute;
    private static BooleanOption dbgCompl;

    private static void fixupCodeCompletionTextComponent(JTextComponent jtc)
    {
        if(!ViManager.getHackFlag(HACK_CC))
            return;

        Module.EditorRegistryRegister(jtc);
        // Add Ctrl-space binding
        Keymap km = JTextComponent.getKeymap(CommandLine.COMMAND_LINE_KEYMAP);
        if(km != null) {
            KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,
                                                InputEvent.CTRL_MASK);
            if(km.getAction(ks) == null) {
                km.addActionForKeyStroke(ks,
                        new TextAction("vi-command-code-completion") {
                            public void actionPerformed(ActionEvent e) {
                                Completion.get().showCompletion();
                            }
                        }
                );
            }
        }
    }

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

        fixupCodeCompletionTextComponent(ceText);

        if(!Options.getOption(Options.autoPopupFN).getBoolean())
            return;

        ceInSubstitute = false;

        Document ceDoc = ceText.getDocument();
        ceDocListen = new CodeComplDocListener();
        ceDoc.addDocumentListener(ceDocListen);

        String text = null;
        try {
            text = ceText.getDocument()
                            .getText(0, ceText.getDocument().getLength());
        } catch(BadLocationException ex) {}

        // see if initial conditions warrent bringing up completion
        if(text != null && text.startsWith("e#")) {
            // Wait till combo's ready to go.
            ceDocListen.didIt = true;
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

    private static class CodeComplDocListener implements DocumentListener {
        boolean didIt;

        public void changedUpdate(DocumentEvent e) {
        }
        public void insertUpdate(DocumentEvent e) {
            ceDocCheck(e.getDocument());
        }
        public void removeUpdate(DocumentEvent e) {
            ceDocCheck(e.getDocument());
        }

        private void ceDocCheck(Document doc) {
            try {
                if(doc.getLength() == 2 && !ceInSubstitute) {
                    if(!didIt && "e#".equals(doc.getText(0, doc.getLength()))) {
                        if(dbgCompl.getBoolean())
                            System.err.println("SHOW:");
                        Completion.get().showCompletion();
                        didIt = true;
                    }
                } else if(doc.getLength() < 2) {
                    if(dbgCompl.getBoolean())
                        System.err.println("HIDE:");
                    Completion.get().hideCompletion();
                    didIt = false;
                }
            } catch (BadLocationException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
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
            NbAppView av;
            Font font = getTxtFont();
            while((av = (NbAppView)ViManager.getTextBuffer(++i)) != null) {
                TopComponent tc = av.getTopComponent();
                int wnum = av.getWNum();
                int flags = 0;
                if(TopComponent.getRegistry().getActivated() == tc)
                    flags |= ITEM_SELECTED;

                ImageIcon icon = tc.getIcon() != null
                        ? new ImageIcon(tc.getIcon())
                        : null;

                // NEEDSWORK: why "== 1"
                //Set<JEditorPane> jepSet = fetchEpFromTC(tc);
                //Set<NbAppView> avSet = fetchAvFromTC(tc);
                //String name = null;
                //if(avSet.size() == 1) {
                //    for (NbAppView av : avSet) {
                //        Document doc = av.getEditor().getDocument();
                //        if(NbEditorUtilities.getDataObject(doc).isModified())
                //            flags |= ITEM_MODIFIED;
                //        name = NbEditorUtilities.getFileObject(doc).getNameExt();
                //    }
                //}
                Document doc = av.getEditor().getDocument();
                if(NbEditorUtilities.getDataObject(doc).isModified())
                    flags |= ITEM_MODIFIED;
                String name = NbEditorUtilities.getFileObject(doc).getNameExt();
                if(name == null)
                    name = factory.getFS().getDisplayFileName(av);
                query.add(new ViCommandCompletionItem(
                                name, String.format("%02d", wnum), icon,
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
            Action act = jtc.getKeymap().getAction(CommandLine.EXECUTE_KEY);
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
