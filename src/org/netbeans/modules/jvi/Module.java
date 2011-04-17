package org.netbeans.modules.jvi;

import java.io.File;
import java.io.IOException;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import org.netbeans.modules.jvi.impl.NbAppView;
import org.netbeans.modules.jvi.impl.NbFactory;
import com.raelity.jvi.core.lib.CcFlag;
import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.ViCaret;
import com.raelity.jvi.ViInitialization;
import com.raelity.jvi.core.lib.PreferencesChangeMonitor;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.options.OptUtil;
import com.raelity.jvi.options.DebugOption;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FilenameFilter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
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
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.Actions;

import org.openide.cookies.EditorCookie;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.ModuleInfo;
import org.openide.modules.ModuleInstall;
import org.openide.modules.SpecificationVersion;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.actions.Presenter;
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
    private static final Logger LOG = Logger.getLogger(Module.class.getName());

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
    private static DebugOption dbgNb;
    private static DebugOption dbgAct;
    private static DebugOption dbgHL;
    private static WeakReference<TopComponent> refOutput;

    public static boolean dbgNb() {
        return dbgNb != null && dbgNb.getBoolean();
    }
    public static boolean dbgAct()
    {
        return dbgAct != null && dbgAct.getBoolean();
    }
    public static boolean dbgHL()
    {
        return dbgHL != null && dbgHL.getBoolean();
    }

    public static final String HACK_CC = "NB6.7 Code Completion";
    public static final String HACK_SCROLL = "NB6.7 Text Scroll";
    
    private static TopComponentRegistryListener topComponentRegistryListener;

    // a SET
    private static Map<TopComponent, Object> tcChecked
            = new WeakHashMap<TopComponent, Object>();

    private static Runnable shutdownHook;

    private static boolean didInit;
    @ServiceProvider(service=ViInitialization.class,
                     path="jVi/init",
                     position=10)
    public static class Init implements ViInitialization
    {
        @Override
        public void init()
        {
            if(didInit)
                return;
            Module.init();
            didInit = true;
        }
    }

    private static void init()
    {
        PropertyChangeListener pcl = new PropertyChangeListener()
        {
            @Override
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

        PreferencesChangeMonitor.setFileHack(
                new PreferencesChangeMonitor.FileHack()
                {
                    @Override
                    public boolean hasKeyValue(Preferences prefs, String child,
                                               String key, String val )
                    {
                        FileObject cf = FileUtil.getConfigFile(
                              "Preferences" + prefs.absolutePath()
                              + "/" + child + ".properties");
                        if(cf != null) {
                            try {
                                String s = cf.asText();
                                if(s.contains(key + "=" + val)) {
                                    return true;
                                }
                            } catch(IOException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                        return false;
                    }
                });
    }

    public static String cid(Object o)
    {
        return ViManager.cid(o);
    }

    public static boolean jViEnabled() {
        return jViEnabled;
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

    public static TopComponent getOutput()
    {
        return refOutput == null ? null : refOutput.get();
    }

    public static String getName(JEditorPane ep)
    {
        return ViManager.getFactory().getFS().getDisplayFileName(
                ViManager.getFactory().getAppView(ep));
    }
    
    /** called when the module is loaded (at netbeans startup time) */
    @Override
    public void restored() {
        if (dbgNb()) {
            System.err.println(MOD + "***** restored *****");
        }
        // Look for an UndoRedo-patch.jar
        File patchDir = InstalledFileLocator.getDefault().locate(
                "modules/patches/org-openide-awt", null, false);
        if(patchDir != null) {
            System.err.println(
                    "Found patch dir \"" + patchDir.getAbsolutePath() + "\"");
            File[] f = patchDir.listFiles(new FilenameFilter() {
                   @Override
                   public boolean accept(File dir, String name)
                   {
                       return name.contains("UndoRedo-patch");
                   }
               });
            if(f != null && f.length > 0) {
                NotifyDescriptor d = new NotifyDescriptor.Message(
                        "Found \"" + f[0].getAbsolutePath() + "\"\n"
                        + "If for jVi undo/redo operation then"
                        + " \n\nremove this file and restart NetBeans\n\n"
                        + "or functionality is lost."
                        + "\nNetBeans now has native support"
                        + "for UndoRedo grouping.",
                        NotifyDescriptor.WARNING_MESSAGE);
                DialogDisplayer.getDefault().notify(d);
            }
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
                    // HACK_CC fixup code-compl registratin and bindings
                    ViManager.putHackMap(HACK_CC, Boolean.TRUE);
                    ViManager.putHackMap(HACK_SCROLL, Boolean.TRUE);
                }
            }
        }

        earlyInit(); // set up the ViFactory
        // Add the actions used by jVi to the System's FileSystem
        KeyActionsFS.injectKeyActionsLayer();

        // in layer.xml Actions/Tools: <file name="o-n-m-jvi-enable.instance">
        // produces the checkbox linked to preferences.
        Preferences prefNode = getModulePreferences();
        if(prefNode.get(PREF_ENABLED, "").isEmpty()) {
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
            ViManager.runInDispatch(true, new RunJViEnable());
        }

        prefNode.addPreferenceChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                System.err.println("PREF CHANGE: " + evt);
                if(evt.getKey().equals(PREF_ENABLED)) {
                    System.err.println("PREF CHANGE TO: " + evt.getNewValue());
                    boolean enabled = getModulePreferences()
                            .getBoolean(PREF_ENABLED, true);
                    EventQueue.invokeLater(enabled
                                           ? new RunJViEnable()
                                           : new RunJViDisable());
                    fixupJviButton();
                }
            }
        });
    }

    ////////////////////////////////////////////////////////////////////
    //
    // NEEDSWORK: TEMPORARY; DELETE when handled by system
    //
    ////////////////////////////////////////////////////////////////////
    private static void fixupJviButton()
    {
        ViManager.runInDispatch(false, new Runnable() {
            @Override public void run()
            {
                if(jviButton != null) {
                    jviButton.setSelected(jViEnabled());
                }
            }
        });
    }
    private static AbstractButton jviButton;
    @ServiceProvider(service=Actions.ButtonActionConnector.class)
    public static class checkActions implements Actions.ButtonActionConnector {

        @Override
        public boolean connect(AbstractButton button, Action action)
        {
            if("jVi".equals(action.getValue(Action.NAME))) {
                if(action instanceof Presenter.Toolbar)
                    return false;

                // Older version of NetBeans, need to work around
                // Bug 197639 - Actions.checkbox has no icon for "off" state

                //System.err.println("connetc: " + action.getValue(Action.NAME));
                jviButton = button;
                Icon jviOnIcon = ImageUtilities.loadImageIcon("org/netbeans/modules/jvi/resources/jViLogoToggle24_selected.png", false);
                jviButton.setSelectedIcon(jviOnIcon);
                EventQueue.invokeLater(new Runnable()
                {
                    @Override public void run() {
                        fixupJviButton();
                    }
                });
            }
            return false;
        }

        @Override
        public boolean connect(JMenuItem item, Action action, boolean popup)
        {
            return false;
        }
    }

    public static void setShutdownHook(Runnable hook) {
        assert shutdownHook == null;
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

        ViManager.runInDispatch(true, new Runnable() {
            @Override
            public void run() {
                factory = new NbFactory();
                ViManager.setViFactory(factory);
            }
        });
    }

    private static class RunJViEnable implements Runnable {
        @Override
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
        @Override
        public void run() {
            if(!jViEnabled)
                return;
            // TODO: get rid of extra undo/redo state?
            ViManager.exitInputMode();
            jViEnabled = false;

            if(dbgNb())
                AppViews.dump(System.err);

            JViOptionWarning.clear();

            KeyBindings.disableKeyBindings();
        }
    }
    
    private static void addDebugOptions()
    {
        dbgNb = OptUtil.createDebugOption(DBG_MODULE);
        OptUtil.setupOptionDesc(DBG_MODULE, "Module interface",
                                "Module and editor kit install/install");

        dbgAct = OptUtil.createDebugOption(DBG_TC);
        OptUtil.setupOptionDesc(DBG_TC, "Top Component",
                                "TopComponent activation/open");

        dbgHL = OptUtil.createDebugOption(DBG_HL);
        OptUtil.setupOptionDesc(DBG_HL, "Hilighting",
                                "Visual/Search highlighting");
    }
    
    private static void addDebugColonCommands() {
        
        //
        // Some debug commands
        //
        ColonCommands.register("dumpTopcomponent", "dumpTopcomponent",
            new ActionListener() {
                @SuppressWarnings("UseOfSystemOutOrSystemErr")
                @Override
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
            }, EnumSet.of(CcFlag.DBG)
        );
        ColonCommands.register("dumpKit", "dumpKit",
            new ActionListener() {
            @Override
                public void actionPerformed(ActionEvent e) {
                    KeyBindings.dumpKit();
                }
            }, EnumSet.of(CcFlag.DBG)
        );
        ColonCommands.register("checkFsActList", "checkFsActList",
            new ActionListener() {
                @SuppressWarnings("UseOfSystemOutOrSystemErr")
                @Override
                public void actionPerformed(ActionEvent e) {
                    for(FsAct fsAct : FsAct.values()) {
                        String path = fsAct.path();
                        System.err.println("checkFsAct: "+fsAct.name()+" "+ path);
                        if(path == null)
                            continue;
                        if(fetchFileSystemAction(fsAct) == null) {
                            System.err.println("\tNot found by getTheObject");
                        }
                    }

                }
            }, EnumSet.of(CcFlag.DBG)
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
    
    public static void execFileSystemAction(FsAct fsAct, ActionEvent e) {
        Action act = fetchFileSystemAction(fsAct);
        if(act != null && act.isEnabled())
            act.actionPerformed(e);
        else
            Util.beep_flush();
    }
    
    /** Get an Action from the file system at the given path.
     * Check if it is a SystemAction, if not then try to create it.
     * @return an Action, null if couldn't get or create one
     */
    public static Action fetchFileSystemAction(FsAct fsAct)
    {
        String path = fsAct.path();
        Action act = null;
        try {
            FileObject fo = FileUtil.getConfigFile(path);
            if(fo != null) {
                Object o = DataObject.find(fo).getLookup().
                            lookup(InstanceCookie.class).instanceCreate();
                if(o instanceof Action)
                    act = (Action)o;
            }
        } catch(Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
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
        @Override
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

                if(tc != null && "Output".equals(tc.getName())) {
                    refOutput = new WeakReference<TopComponent>(tc);
                }

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
                    KeyBindings.removeKnownEditor(av.getEditor());
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
    
    private static void tcDumpInfo(Object o, String tag)
    {
        if(!dbgAct())
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
    public static TopComponent getKnownTopComponent(JEditorPane editorPane)
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
}
