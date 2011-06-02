/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.raelity.jvi.nb.patches.org.openide.windows;

import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JViewport;
import org.netbeans.modules.jvi.reflect.NbWindows;
import org.netbeans.modules.jvi.spi.WindowsProvider;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.openide.windows.WindowManager.SiblingState;
import org.openide.windows.WindowManager.WeightCalculator;

/**
 *
 * @author Ernie Rael <err at raelity.com>
 */
@ServiceProvider(service=WindowsProvider.class)
public final class WindowsProviderUsingPatch implements WindowsProvider
{
    @Override
    public void move(Mode m, EditorHandle eh)
    {
        // Implement this through reflection (at least for now),
        // maybe m.dockInto(tc) will get fixed someday.
        NbWindows.userDroppedTopComponents(m, new TopComponent[] {eh.getTC()});
    }

    @Override
    public void addModeOnSide(Mode m, String side, EditorHandle eh)
    {
        WindowManager.getDefault().addModeOnSide(m, side, eh.getTC());
    }

    @Override
    public void addModeAround(Mode m, String side, EditorHandle eh)
    {
        WindowManager.getDefault().addModeAround(m, side, eh.getTC());
    }

    @Override
    public void setSize(Mode m, double targetWeight)
    {
        WindowManager.getDefault().adjustSizes(m, new AdjustSize(targetWeight));
    }

    @Override
    public double getWeight(int n, String orientation, EditorHandle eh)
    {
        Component splitter = eh.getParentSplitter();
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
        Dimension dSplitter = splitter == null ? dMode : splitter.getSize();
        // The general formula is: w = (n * perChar + decoration) / total
        // where decoration is the extra stuff in the mode
        // add a few extra pixels to get a complete line/column
        double targetWeight;
        if(orientation.equals(UP_DOWN)) {
            targetWeight = (n * eh.getLineHeight()
                            + (dMode.height - dEd.height)
                            + 4) / (double)dSplitter.height;
        } else {
            targetWeight = (n * eh.getMaxCharWidth()
                            + (dMode.width - dEd.width)
                            + 4) / (double)dSplitter.width;
        }
        return targetWeight;
    }

    /** an ugly hack */
    @Override
    public Component findModePanel(Component c)
    {
        return NbWindows.findModePanel(c);
    }

    private class AdjustSize implements WeightCalculator
    {
        double targetWeight;

        public AdjustSize(double targetWeight)
        {
            this.targetWeight = targetWeight;
        }

        @Override
        public List<Double> getWeights(SiblingState currentState)
        {
            List<Double> w = currentState.getWeights();
            if(targetWeight >= 1D)
                targetWeight = 0D;
            if(targetWeight == 0D) {
                // make equal
                for(int i = 0; i < w.size(); i++) {
                    w.set(i, 1D / w.size());
                }
            } else {
                double otherWeight = (1 - targetWeight) / (w.size() - 1);
                for(int i = 0; i < w.size(); i++) {
                    w.set(i, i == currentState.getTargetIndex()
                            ? targetWeight : otherWeight);
                }
            }
            return w;
        }

    }

}
