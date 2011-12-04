/*
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is jvi - vi editor clone.
 *
 * The Initial Developer of the Original Code is Ernie Rael.
 * Portions created by Ernie Rael are
 * Copyright (C) 2000-2010 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */
package org.netbeans.modules.jvi.spi;

import java.awt.Component;
import java.awt.Dimension;

import org.openide.windows.Mode;
import org.openide.windows.TopComponent;

/**
 * Window commands provided by NB windowing system.
 * Useful to implement split/move/resize.
 * <p/>
 * The getWeight/setSize is too bad, would rather simply have a
 * setSize(int n, Orientation orientation, EditorHandle eh)
 * but the calculation of the weight depends on knowing sizes of things
 * which can not be reliably obtained in NB, especially just after changing
 * things around.
 * <p/>
 * Expect two implementations, one partial using reflection
 * and one more fully featured based on patch (possibly future work).
 *
 * @author Ernie Rael <err at raelity.com>
 */
public interface WindowsProvider
{
    public static final String LEFT_RIGHT = "LEFT_RIGHT";
    public static final String UP_DOWN = "UP_DOWN";

    interface EditorHandle {
        TopComponent getTC();
        Component getEd();
    }

    interface EditorSizerArgs {
        /**
         * Editor to split.
         */
        Component getEditorToSplit();
        /**
         * Size of container being split.
         */
        Dimension getResizeTargetContainer();
        double charHeight();
        double charWidth();
    }

    /**
     * move is here because Mode.dockInto(editorTC) closes TC.
     */
    void move(Mode m, EditorHandle eh);

    /**
     * This is used for split. Creates a new mode an puts the editorTC into it.
     */
    void addModeOnSide(Mode m, String side, EditorHandle eh);

    /**
     * Like addModeOnSide, but the new mode touches the outermost
     * editor container and spans its full width/height.
     */
    void addModeAround(Mode m, String side, EditorHandle eh);

    /**
     * Set the weight of the cells within this splitter.
     * Do nothing if the number of weights does not match number children.
     */
    void setWeights(Component splitter, double[] weights);

    /**
     * @param n number of cols or lines depending on orientation.
     * @param orientation
     * @param eh
     * @return target weight for eh's mode.
     */
    double getWeight(double n, String orientation, EditorSizerArgs eh);

    /** part of the size calculation */
    Component findModePanel(Component c);

    /** These are great for toggle */
    void minimizeMode(Mode m);
    void restoreMode(Mode slidingMode, Mode modeToRestore);
}
