package org.netbeans.modules.jvi;

import com.raelity.jvi.options.BooleanOption;
import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.ViInitialization;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.options.OptUtil;
import com.raelity.jvi.swing.SwingFactory;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.manager.Scheduler;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
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
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.modules.editor.NbEditorUtilities;
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


    private static void closeTC(JEditorPane ep, TopComponent tc) {
        AppViews.close(fetchAvFromTC(tc, ep));
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
        AppViews.deactivateCurrent(av);
    }
    
    // This was private, but there are times when a TopComponent with
    // an editor pane sneaks through the TC open/activation logic (DiffExecuter)
    private static void activateTC(JEditorPane ep, TopComponent tc, String tag) {
        if(ep != null)
            KeyBindings.checkCaret(ep);
        NbAppView av = fetchAvFromTC(tc, ep);
        AppViews.activate(av, tag);
    }

    private static void registerTC(NbAppView av, String tag) {
        AppViews.register(av, tag);
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
                        Scheduler.requestSwitch(av.getEditor());
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
    

    static void runAfterActivateSwitch(Runnable runnable) { // NEEDSWORK: use focus
    }

    static private void doRunAfterActivateSwitch() { // NEEDSWORK: use focus
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
