/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.jvi.reflect;

import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;
import javax.swing.JViewport;

import org.netbeans.modules.jvi.spi.WindowsProvider;
import org.netbeans.modules.jvi.spi.WindowsProvider.EditorHandle;
import org.netbeans.modules.jvi.spi.WindowsProvider.EditorSizerArgs;
import org.openide.util.Lookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class NbWindows
{
    private NbWindows() { }

    public static WindowsProvider getReflectionWindowsProvider()
    {
        return new WindowsProvider() {

            @Override
            public void move(Mode m, EditorHandle eh)
            {
                userDroppedTopComponents(m, eh.getTC());
            }

            @Override
            public void addModeOnSide(Mode m, String side, EditorHandle eh)
            {
                userDroppedTopComponents(m, eh.getTC(), side);
            }

            @Override
            public void addModeAround(Mode m, String side, EditorHandle eh)
            {
                userDroppedTopComponentsAroundEditor(eh.getTC(), side);
            }

            @Override
            public void setWeights(Component splitter, double[] weights)
            {
                NbWindows.setWeights(splitter, weights);
            }

            @Override
            public double getWeight(double n, String orientation,
                                    EditorSizerArgs eh)
            {
                return NbWindows.getWeight(n, orientation, eh);
            }

            @Override
            public Component findModePanel(Component c)
            {
                return NbWindows.findModePanel(c);
            }

            @Override
            public void minimizeMode(Mode m)
            {
                userMinimizedMode(m);
            }

            @Override
            public void restoreMode(Mode slidingMode, Mode modeToRestore)
            {
                userRestoredMode(slidingMode, modeToRestore);
            }
        };
    }

    public static double getWeight(double n, String orientation,
                                   EditorSizerArgs eh)
    {
        if(n == 0)
            return 0;

        // NEEDSWORK: splitter should never be null
        //            fix it in WindowTreeBuilder

        Component c;
        c = eh.getEditorToSplit();
        if(c.getParent() instanceof JViewport)
            c = c.getParent();
        Dimension dEd = c.getSize();
        Dimension dMode = findModePanel(eh.getEditorToSplit()).getSize();
        Dimension dContainer = eh.getResizeTargetContainer();
        // The general formula is: w = (n * perChar + decoration) / total
        // where decoration is the extra stuff in the mode
        // add a few extra pixels to get a complete line/column
        double targetWeight;
        if(orientation.equals("UP_DOWN")) {
            targetWeight = (n * eh.charHeight() + (dMode.height - dEd.height))
                                / dContainer.height;
        } else {
            targetWeight = (n * eh.charWidth() + (dMode.width - dEd.width))
                                 / dContainer.width;
        }
        return targetWeight;
    }

    /** an ugly hack */
    public static Component findModePanel(Component c)
    {
        Component modePanel = null;
        do {
            if("org.netbeans.core.windows.view.ui.DefaultSplitContainer$ModePanel".equals(c.getClass().getName())) {
                modePanel = c;
                break;
            }
        } while((c = c.getParent()) != null);
        return modePanel;
    }

    private static void userDroppedTopComponents(Mode mode, TopComponent tc)
    {
        try {
            Object wmi = WindowManager.getDefault();
            Object central = meth_getCentral().invoke(wmi);

            meth_userDroppedTopComponents().invoke(central, mode, getDrop(tc));
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void userDroppedTopComponents(
            Mode mode, TopComponent tc, String side)
    {
        try {
            Object wmi = WindowManager.getDefault();
            Object central = meth_getCentral().invoke(wmi);

            meth_userDroppedTopComponents_side()
                    .invoke(central, mode, getDrop(tc), side);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    /** different from NB in that modeKind is forced to editor */
    private static void userDroppedTopComponentsAroundEditor(
            TopComponent tc, String side)
    {
        try {
            Object wmi = WindowManager.getDefault();
            Object central = meth_getCentral().invoke(wmi);

            if(is70()) {
                // 1 ==> MODE_KIND_EDITOR
                meth_userDroppedTopComponentsAroundEditor()
                        .invoke(central, getDrop(tc), side, 1);
            } else {
                meth_userDroppedTopComponentsAroundEditor()
                        .invoke(central, getDrop(tc), side);
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void userMinimizedMode( Mode mode ) {
        try {
            Object wmi = WindowManager.getDefault();

            // NOTE: exception if not 7.1 or later
            meth_userMinimizedMode().invoke(wmi, mode);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void userRestoredMode( Mode slidingMode, Mode modeToRestore ) {
        try {
            Object wmi = WindowManager.getDefault();

            // NOTE: exception if not 7.1 or later
            meth_userRestoredMode().invoke(wmi, slidingMode, modeToRestore);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Invoke setWeights on splitter/MultiSplitPane.
     * See comment that follows for code that was tested in a NB patch.
     * @param splitter
     * @param weights
     */
    public static void setWeights(Component splitter, double[] weights)
    {
        populateCoreWindowMethods();
        if(        null == meth_getCellCount_MSP
                || null == meth_cellAt_MSP
                || null == field_userMovedSplit_MSP

                || null == meth_getComponent_MSC
                || null == field_initialSplitWeight_MSC
                || null == field_requiredSize_MSC)
            return;
        Exception ex1 = null;
        try {
            int nCell = (Integer)meth_getCellCount_MSP.invoke(splitter);
            if(nCell != weights.length)
                return;
            for(int i = 0; i < weights.length; i++) {
                Object cell = meth_cellAt_MSP.invoke(splitter, i);
                field_initialSplitWeight_MSC.set(cell, weights[i]);
                field_requiredSize_MSC.set(cell, -1);
                ((Component)meth_getComponent_MSC.invoke(cell)).setSize(0,0);
            }
            field_userMovedSplit_MSP.set(splitter, true);
            ((JPanel)splitter).revalidate();
        } catch(IllegalAccessException ex) {
            ex1 = ex;
        } catch(IllegalArgumentException ex) {
            ex1 = ex;
        } catch(InvocationTargetException ex) {
            ex1 = ex;
        }
        if(ex1 != null)
            Logger.getLogger(NbWindows.class.getName())
                    .log(Level.SEVERE, null, ex1);
    }

    private static boolean is70()
    {
        return meth_userDroppedTopComponents_70 != null;
    }

    private static Object getDrop(TopComponent tc)
    {
        try {
            return is70()
                    ? new TopComponent[] {tc}
                    : constr_topComponentDraggable().newInstance(tc);
        } catch(InstantiationException ex) {
        } catch(IllegalAccessException ex) {
        } catch(IllegalArgumentException ex) {
        } catch(InvocationTargetException ex) {
        }
        return null;
    }

    private static Method meth_getCentral;

    private static Method meth_userDroppedTopComponents_70; //mode,TC[]
    private static Method meth_userDroppedTopComponents_side_70;//mode,TC[],side)
    private static Method meth_userDroppedTopComponentsAroundEditor_70; //TC[],side

    private static Method meth_userDroppedTopComponents_71; //mode,Drag
    private static Method meth_userDroppedTopComponents_side_71;//mode,Drag,side)
    private static Method meth_userDroppedTopComponentsAroundEditor_71; //Drag,side
    private static Method meth_userMinimizedMode_71;
    private static Method meth_userRestoredMode_71;
    private static Constructor<?> constr_topComponentDraggable_71;//TC

    private static Method meth_getCellCount_MSP;
    private static Method meth_cellAt_MSP;
    private static Field field_userMovedSplit_MSP;

    private static Method meth_getComponent_MSC;
    private static Field field_initialSplitWeight_MSC;
    private static Field field_requiredSize_MSC;

    private static Method meth_getCentral() {
        populateCoreWindowMethods();
        return meth_getCentral;
    }
    private static Constructor<?> constr_topComponentDraggable() {
        populateCoreWindowMethods();
        return constr_topComponentDraggable_71;
    }
    private static Method meth_userDroppedTopComponents() {
        populateCoreWindowMethods();
        return is70()
                ? meth_userDroppedTopComponents_70
                : meth_userDroppedTopComponents_71;
    }
    static Method meth_userDroppedTopComponents_side() {
        populateCoreWindowMethods();
        return is70()
                ? meth_userDroppedTopComponents_side_70
                : meth_userDroppedTopComponents_side_71;
    }
    static Method meth_userDroppedTopComponentsAroundEditor() {
        populateCoreWindowMethods();
        return is70()
               ? meth_userDroppedTopComponentsAroundEditor_70
               : meth_userDroppedTopComponentsAroundEditor_71;
    }
    static Method meth_userMinimizedMode() {
        populateCoreWindowMethods();
        return is70()
               ? null
               : meth_userMinimizedMode_71;
    }
    static Method meth_userRestoredMode() {
        populateCoreWindowMethods();
        return is70()
               ? null
               : meth_userRestoredMode_71;
    }

    private static void populateCoreWindowMethods() {
        if(meth_userDroppedTopComponents_70 != null
                || meth_userDroppedTopComponents_71 != null)
            return;
        populateCoreWindowMethods_70();
        populateCoreWindowMethods_71();
        populateCoreWindowMethodsResize();
    }

    private static void populateCoreWindowMethodsResize() {
        Object wmi = WindowManager.getDefault();
        Exception ex1 = null;
        try {
            Method[] meths = wmi.getClass().getDeclaredMethods();
            for(Method m : meths) {
                if(m.getName().equals("getCentral")) {
                    m.setAccessible(true);
                    meth_getCentral = m;
                    break;
                }
            }

            ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
            Class<?> c_multiSplitPane = cl.loadClass(
                    "org.netbeans.core.windows.view.ui.MultiSplitPane");
            Class<?> c_multiSplitCell = cl.loadClass(
                    "org.netbeans.core.windows.view.ui.MultiSplitCell");

            meths = c_multiSplitPane.getDeclaredMethods();
            for(Method m : meths) {
                if(m.getName().equals("getCellCount")) {
                    m.setAccessible(true);
                    meth_getCellCount_MSP = m;
                }
                if(m.getName().equals("cellAt")) {
                    m.setAccessible(true);
                    meth_cellAt_MSP = m;
                }
            }
            Field[] fields = c_multiSplitPane.getDeclaredFields();
            for(Field f : fields) {
                if(f.getName().equals("userMovedSplit")) {
                    f.setAccessible(true);
                    field_userMovedSplit_MSP = f;
                }
            }

            meths = c_multiSplitCell.getDeclaredMethods();
            for(Method m : meths) {
                if(m.getName().equals("getComponent")) {
                    m.setAccessible(true);
                    meth_getComponent_MSC = m;
                }
            }
            fields = c_multiSplitCell.getDeclaredFields();
            for(Field f : fields) {
                if(f.getName().equals("initialSplitWeight")) {
                    f.setAccessible(true);
                    field_initialSplitWeight_MSC = f;
                }
                if(f.getName().equals("requiredSize")) {
                    f.setAccessible(true);
                    field_requiredSize_MSC = f;
                }
            }
        } catch(SecurityException ex) {
            ex1 = ex;
        } catch(ClassNotFoundException ex) {
            ex1 = ex;
        }
        // if(ex1 != null)
        //     ex1.printStackTrace();
    }
    private static void populateCoreWindowMethods_70() {
        if(meth_userDroppedTopComponents_70 == null) {
            Object wmi = WindowManager.getDefault();
            Exception ex1 = null;
            try {
                Method[] meths = wmi.getClass().getDeclaredMethods();
                for(Method m : meths) {
                    if(m.getName().equals("getCentral")) {
                        m.setAccessible(true);
                        meth_getCentral = m;
                        break;
                    }
                }

                ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
                Class<?> c_central = cl.loadClass(
                        "org.netbeans.core.windows.Central");
                Class<?> c_modeImpl = cl.loadClass(
                        "org.netbeans.core.windows.ModeImpl");
                TopComponent[] tcs = new TopComponent[0];
                meth_userDroppedTopComponents_70
                        = c_central.getMethod("userDroppedTopComponents",
                                              c_modeImpl, tcs.getClass());
                meth_userDroppedTopComponents_70.setAccessible(true);
                meth_userDroppedTopComponents_side_70
                        = c_central.getMethod("userDroppedTopComponents",
                                              c_modeImpl, tcs.getClass(),
                                              String.class);
                meth_userDroppedTopComponents_side_70.setAccessible(true);
                meth_userDroppedTopComponentsAroundEditor_70
                        = c_central.getMethod(
                                "userDroppedTopComponentsAroundEditor",
                                tcs.getClass(), String.class, int.class);
                meth_userDroppedTopComponentsAroundEditor_70.setAccessible(true);
            } catch(NoSuchMethodException ex) {
                ex1 = ex;
            } catch(SecurityException ex) {
                ex1 = ex;
            } catch(ClassNotFoundException ex) {
                ex1 = ex;
            }
        }
    }

    private static void populateCoreWindowMethods_71() {
        if(meth_userDroppedTopComponents_71 == null) {
            Object wmi = WindowManager.getDefault();
            Exception ex1 = null;
            try {
                Method[] meths = wmi.getClass().getDeclaredMethods();
                for(Method m : meths) {
                    if(m.getName().equals("getCentral")) {
                        m.setAccessible(true);
                        meth_getCentral = m;
                        break;
                    }
                }

                ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
                Class<?> c_wmi = cl.loadClass(
                        "org.netbeans.core.windows.WindowManagerImpl");
                Class<?> c_tcDraggable = cl.loadClass(
                        "org.netbeans.core.windows.view.dnd.TopComponentDraggable");
                Class<?> c_central = cl.loadClass(
                        "org.netbeans.core.windows.Central");
                Class<?> c_modeImpl = cl.loadClass(
                        "org.netbeans.core.windows.ModeImpl");
                constr_topComponentDraggable_71 = c_tcDraggable
                        .getDeclaredConstructor(TopComponent.class);
                constr_topComponentDraggable_71.setAccessible(true);
                meth_userDroppedTopComponents_71
                        = c_central.getMethod("userDroppedTopComponents",
                                              c_modeImpl, c_tcDraggable);
                meth_userDroppedTopComponents_71.setAccessible(true);
                meth_userDroppedTopComponents_side_71
                        = c_central.getMethod("userDroppedTopComponents",
                                              c_modeImpl, c_tcDraggable,
                                              String.class);
                meth_userDroppedTopComponents_side_71.setAccessible(true);
                meth_userDroppedTopComponentsAroundEditor_71
                        = c_central.getMethod(
                                "userDroppedTopComponentsAroundEditor",
                                c_tcDraggable, String.class);
                meth_userDroppedTopComponentsAroundEditor_71.setAccessible(true);

                meth_userMinimizedMode_71
                        = c_wmi.getMethod(
                                "userMinimizedMode", c_modeImpl);
                meth_userMinimizedMode_71.setAccessible(true);
                meth_userRestoredMode_71
                        = c_wmi.getMethod(
                                "userRestoredMode", c_modeImpl, c_modeImpl);
                meth_userRestoredMode_71.setAccessible(true);
            } catch(NoSuchMethodException ex) {
                ex1 = ex;
            } catch(SecurityException ex) {
                ex1 = ex;
            } catch(ClassNotFoundException ex) {
                ex1 = ex;
            }
        }
    }

    // From Central:
    //     public void userDroppedTopComponents(ModeImpl mode,
    //                                          TopComponent[] tcs) {
    //         updateViewAfterDnD(moveTopComponentsIntoMode(mode, tcs));
    //     }
    // tried doing:
    //          moveTopComponentsIntoMode(...)
    // that doesn't work.
    //
    // Then tried:
    //          moved = moveTopComponentsIntoMode(...)
    //          if(moved)
    //              switchMaximizedMode(null)
    // that doesn't work
    //
    // Then tried:
    //
    //          moved = moveTopComponentsIntoMode(...)
    //          updateViewAfterDnD(moved)
    // so all the little pieces of userDroppedTopComponents are needed
    //

    // static Method meth_setWeights_multiSplitPane() {
    //     populateCoreWindowMethods();
    //     return meth_setWeights_multiSplitPane;
    // }

    // private static Method meth_setWeights_multiSplitPane; //TC[],side

/*
    public static void oldSetWeights(Component splitter, double[] weights)
    {
        Method setWeights = meth_setWeights_multiSplitPane();
        if(setWeights == null)
            return;
        try {
            setWeights.invoke(splitter, weights);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }
    // FROM MultiSplitPane
    public void setWeights(double[] splitWeights) {
        if(splitWeights.length != cells.size())
            return;
        for(int i = 0; i < splitWeights.length; i++) {
            double d = splitWeights[i];
            cells.get(i).setWeight(d);
        }
        userMovedSplit = true; // send new dimensions to model
        revalidate();
    }
    // FROM MultiSplitCell
    void setWeight(double weight) {
        initialSplitWeight = weight;
        // Force calculation based on new weight
        requiredSize = -1;
        getComponent().setSize(0,0);
    }
*/

}
