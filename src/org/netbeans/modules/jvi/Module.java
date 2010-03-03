package org.netbeans.modules.jvi;

import com.raelity.jvi.ViCaret;
import com.raelity.jvi.options.BooleanOption;
import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.ViInitialization;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.options.OptUtil;
import com.raelity.jvi.manager.AppViews;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;

import org.openide.cookies.EditorCookie;
import org.openide.cookies.InstanceCookie;
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

    public static final String MOD
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

    // a SET
    private static Map<TopComponent, Object> tcChecked
            = new WeakHashMap<TopComponent, Object>();

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

    public static boolean jViEnabled() {
        return jViEnabled;
    }

    public static boolean isDbgNb() {
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
    public static synchronized void earlyInit() {
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

            KeyBindings.enableKeyBindings();
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
                AppViews.dump(System.err);

            JViOptionWarning.clear();
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
                    KeyBindings.dumpKit();
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
        if(ViManager.isDebugAtHome() && !FsAct.getFsActList().contains(path)) {
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

    
    //////////////////////////////////////////////////////////////////////
    //
    // Some methods to access a TC's AppView(s)
    // A TC may have more than one JEditorPane.
    //
    
    /** This class monitors the TopComponent registry and issues
     * <ul>
     * <li> ViManager.activate("debuginfo", tc) </li>
     * <li> ViManager.deactivateCurrent(ep, tc) </li>
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
                    tcDumpInfo(tc, "activated oldTC");

                    for (NbAppView av : NbAppView.fetchAvFromTC(tc)) {
                        AppViews.deactivate(av);
                    }
                }

                tc = (TopComponent) evt.getNewValue();
                if(tc != null) {
                    if(!tcChecked.containsKey(tc)) {
                        pokeTC(tc, true); // force instantiation of editor panes

                        List<JEditorPane> l = getDescendentJep(tc);
                        for (JEditorPane ep : l) {
                            NbAppView av = NbAppView.updateAppViewForTC(
                                    "P_ACTV", tc, ep);
                        }
                        tcChecked.put(tc, null);
                    }

                    tcDumpInfo(tc, "activated newTC");
                }
            } else if(evt.getPropertyName()
                    .equals(TopComponent.Registry.PROP_TC_OPENED)) {
                TopComponent tc = (TopComponent) evt.getNewValue();

                boolean isEditor = pokeTC(tc, false);
                tcDumpInfo(tc, "open");

                boolean createdAppView = false;
                List<JEditorPane> l = getDescendentJep(tc);
                for (JEditorPane ep : l) {
                    if(!(ep.getCaret() instanceof ViCaret)) {
                        // skip editors that don't have the right caret
                        continue;
                    }
                    // if it is not an editor then treat it like a nomad
                    NbAppView av = NbAppView.updateAppViewForTC(
                            "P_OPEN", tc, ep, !isEditor);
                    createdAppView = true;
                }
                if(isEditor && !createdAppView) {
                    NbAppView av = NbAppView.updateAppViewForTC(
                            "P_OPEN_LAZY", tc, null);
                }
            } else if(evt.getPropertyName()
                    .equals(TopComponent.Registry.PROP_TC_CLOSED)) {
                TopComponent tc = (TopComponent) evt.getNewValue();
                for (NbAppView av : NbAppView.fetchAvFromTC(tc)) {
                    tcDumpInfo(tc, "close");
                    AppViews.close(av);
                }
            }
        }
    }

    static List<JEditorPane> getDescendentJep(Component parent) {
        List<JEditorPane> l = new ArrayList<JEditorPane>(2);
        getDescendentJep(parent, l, true);
        return l;
    }
    static void getDescendentJep(
            Component parent, List<JEditorPane> l, boolean skipNonJvi)
    {
        if (parent instanceof Container) {
            Component components[] = ((Container)parent).getComponents();
            for (int i = 0 ; i < components.length ; i++) {
                Component comp = components[i];
                if (comp != null) {
                    if(comp instanceof JEditorPane) {
                        if(skipNonJvi && !(((JEditorPane)comp).getCaret()
                                            instanceof ViCaret))
                            continue;
                        l.add((JEditorPane)comp);
                    } else if (comp instanceof Container) {
                        getDescendentJep(comp, l, skipNonJvi);
                    }
                }
            }
        }
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
        List<JEditorPane> panes = new ArrayList<JEditorPane>(2);
        getDescendentJep(tc, panes, false); // getListDeprecated non jvi editors
        Mode mode = WindowManager.getDefault().findMode(tc);
        if(dbgAct.getBoolean()) {
            System.err.format("trackTC: %s: %s:%s '%s' : nPanes = %d\n",
                    tag,
                    tc.getDisplayName(), cid(tc),
                    (mode == null ? "null" : mode.getName()),
                    panes.size());
            for (JEditorPane ep : panes) {
                NbAppView av = NbAppView.fetchAvFromTC(tc, ep);
                System.err.printf("\tep:%d %s tc: %s isEditable %b %s\n",
                        av != null ? av.getWNum() : 0,
                        cid(ep), ancestorStringTC(ep), ep.isEditable(),
                        !(ep.getCaret() instanceof ViCaret) ? "NOT-JVI" : "");
            }
        }
    }

    /**
     *
     * @param tc top component to examine
     * @param force when true, instantiate the editor panes
     * @return true if the tc is an editor
     */
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
}
