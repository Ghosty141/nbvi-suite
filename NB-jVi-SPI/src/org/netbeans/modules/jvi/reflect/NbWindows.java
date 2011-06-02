/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.jvi.reflect;

import java.awt.Component;
import java.lang.reflect.Method;
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
            public void setSize(Mode m, double weight)
            {
                // not supported through reflection
            }

            @Override
            public double getWeight(int n, String orientation, EditorHandle eh)
            {
                // not supported through reflection
                return 0;
            }

            @Override
            public Component findModePanel(Component c)
            {
                return NbWindows.findModePanel(c);
            }
        };
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

    private static Method meth_getCentral;
    private static Method meth_userDroppedTopComponents; //mode,TC[]
    private static Method meth_userDroppedTopComponents_side;//mode,TC[],side)
    private static Method meth_userDroppedTopComponentsAroundEditor; //TC[],side
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

    private static void populateCoreWindowMethods() {
        if(meth_userDroppedTopComponents== null) {
            Object wmi = WindowManager.getDefault();
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
                TopComponent[] tcs = new TopComponent[0];
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
            } catch(NoSuchMethodException ex) {
            } catch(SecurityException ex) {
            } catch(ClassNotFoundException ex) {
            }
        }
    }

}
