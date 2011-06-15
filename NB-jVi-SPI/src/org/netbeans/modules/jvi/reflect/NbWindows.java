/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.jvi.reflect;

import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import javax.swing.JViewport;
import org.netbeans.modules.jvi.spi.WindowsProvider;
import org.netbeans.modules.jvi.spi.WindowsProvider.EditorHandle;
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
                userDroppedTopComponents(m, new TopComponent[] {eh.getTC()});
            }

            @Override
            public void addModeOnSide(Mode m, String side, EditorHandle eh)
            {
                userDroppedTopComponents(m, new TopComponent[] {eh.getTC()},
                                         side);
            }

            @Override
            public void addModeAround(Mode m, String side, EditorHandle eh)
            {
                userDroppedTopComponentsAroundEditor(
                        new TopComponent[] {eh.getTC()}, side);
            }

            @Override
            public void setWeights(Component splitter, double[] weights)
            {
                NbWindows.setWeights(splitter, weights);
            }

            @Override
            public double getWeight(double n, String orientation, EditorHandle eh)
            {
                return NbWindows.getWeight(n, orientation, eh);
            }

            @Override
            public Component findModePanel(Component c)
            {
                return NbWindows.findModePanel(c);
            }
        };
    }

    public static double getWeight(double n, String orientation, EditorHandle eh)
    {
        Dimension resizeTargetContainer = eh.getResizeTargetContainer();
        if(n == 0)
            return 0;

        // NEEDSWORK: splitter should never be null
        //            fix it in WindowTreeBuilder

        Component c;
        c = eh.getEd();
        if(c.getParent() instanceof JViewport)
            c = c.getParent();
        Dimension dEd = c.getSize();
        Dimension dMode = findModePanel(eh.getEd()).getSize();
        // NEEDSWORK: splitter should never be null
        //            fix it in WindowTreeBuilder
        Dimension dContainer = resizeTargetContainer == null
                ? dMode : resizeTargetContainer;
        // The general formula is: w = (n * perChar + decoration) / total
        // where decoration is the extra stuff in the mode
        // add a few extra pixels to get a complete line/column
        double targetWeight;
        if(orientation.equals("UP_DOWN")) {
            targetWeight = (n * eh.getLineHeight()
                            + (dMode.height - dEd.height)
                            + 0) / (double)dContainer.height;
        } else {
            targetWeight = (n * eh.getMaxCharWidth()
                            + (dMode.width - dEd.width)
                            + 0) / (double)dContainer.width;
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

    public static void userDroppedTopComponents(Mode mode, TopComponent[] tcs)
    {
        try {
            Object wmi = WindowManager.getDefault();
            Object central = meth_getCentral().invoke(wmi);

            meth_userDroppedTopComponents().invoke(central, mode, tcs);

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
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void userDroppedTopComponents(
            Mode mode, TopComponent[] tcs, String side)
    {
        try {
            Object wmi = WindowManager.getDefault();
            Object central = meth_getCentral().invoke(wmi);

            meth_userDroppedTopComponents_side()
                    .invoke(central, mode, tcs, side);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    /** different from NB in that modeKind is forced to editor */
    public static void userDroppedTopComponentsAroundEditor(
            TopComponent[] tcs, String side)
    {
        try {
            Object wmi = WindowManager.getDefault();
            Object central = meth_getCentral().invoke(wmi);

            meth_userDroppedTopComponentsAroundEditor()
                    .invoke(central, tcs, side, 1);// 1 ==> MODE_KIND_EDITOR
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

    private static Method meth_getCentral;
    private static Method meth_userDroppedTopComponents; //mode,TC[]
    private static Method meth_userDroppedTopComponents_side;//mode,TC[],side)
    private static Method meth_userDroppedTopComponentsAroundEditor; //TC[],side
    // private static Method meth_setWeights_multiSplitPane; //TC[],side

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
    private static Method meth_userDroppedTopComponents() {
        populateCoreWindowMethods();
        return meth_userDroppedTopComponents;
    }
    static Method meth_userDroppedTopComponents_side() {
        populateCoreWindowMethods();
        return meth_userDroppedTopComponents_side;
    }
    static Method meth_userDroppedTopComponentsAroundEditor() {
        populateCoreWindowMethods();
        return meth_userDroppedTopComponentsAroundEditor;
    }
    // static Method meth_setWeights_multiSplitPane() {
    //     populateCoreWindowMethods();
    //     return meth_setWeights_multiSplitPane;
    // }

    private static void populateCoreWindowMethods() {
        if(meth_userDroppedTopComponents== null) {
            Object wmi = WindowManager.getDefault();
            Exception ex1;
            try {
                // could use WeakRef's for method's
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
                Class<?> c_multiSplitPane = cl.loadClass(
                        "org.netbeans.core.windows.view.ui.MultiSplitPane");
                Class<?> c_multiSplitCell = cl.loadClass(
                        "org.netbeans.core.windows.view.ui.MultiSplitCell");
                TopComponent[] tcs = new TopComponent[0];
                double[] ds = new double[0];
                meth_userDroppedTopComponents
                        = c_central.getMethod("userDroppedTopComponents",
                                              c_modeImpl, tcs.getClass());
                meth_userDroppedTopComponents.setAccessible(true);
                meth_userDroppedTopComponents_side
                        = c_central.getMethod("userDroppedTopComponents",
                                              c_modeImpl, tcs.getClass(),
                                              String.class);
                meth_userDroppedTopComponents_side.setAccessible(true);
                meth_userDroppedTopComponentsAroundEditor
                        = c_central.getMethod(
                                "userDroppedTopComponentsAroundEditor",
                                tcs.getClass(), String.class, int.class);
                meth_userDroppedTopComponentsAroundEditor.setAccessible(true);
                // meth_setWeights_multiSplitPane = c_multiSplitPane.getMethod(
                //         "setWeights", ds.getClass());

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
            } catch(NoSuchMethodException ex) {
                ex1 = ex;
            } catch(SecurityException ex) {
                ex1 = ex;
            } catch(ClassNotFoundException ex) {
                ex1 = ex;
            }
        }
    }

}
