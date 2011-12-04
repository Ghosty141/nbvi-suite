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
 * Copyright (C) 2000 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.jvi;

import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.netbeans.modules.jvi.impl.NbOptionsNode;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.ui.options.OptionsPanel;

@OptionsPanelController.TopLevelRegistration(
    position=1000,
    categoryName="#OptionsCategory_Name_jViConfig",
    iconBase="org/netbeans/modules/jvi/resources/jViLogoSimple32.png",
    keywords="#OptionsCategory_Keywords_jViConfig",
    keywordsCategory="jViConfig"
)
public final class jViOptionsPanelController extends OptionsPanelController
{

    //private jViConfigPanel panel;
    private JPanel panel;
    private OptionsPanel optionsPanel;
    private JComponent hack;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;

    @Override
    public void update()
    {
        getPanel().load();
        changed = false;
    }

    @Override
    public void applyChanges()
    {
        getPanel().ok();
        changed = false;
    }

    @Override
    public void cancel()
    {
        getPanel().cancel();
        changed = false;
    }

    @Override
    public boolean isValid()
    {
        return getPanel().valid();
    }

    @Override
    public boolean isChanged()
    {
        return changed;
    }

    @Override
    public HelpCtx getHelpCtx()
    {
        return null; // new HelpCtx("...ID") if you have a help set
    }

    @Override
    public JComponent getComponent(Lookup masterLookup)
    {
        return getDisplayPanel();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l)
    {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l)
    {
        pcs.removePropertyChangeListener(l);
    }

    private JPanel getDisplayPanel()
    {
        getPanel();
        return panel;
    }

    private OptionsPanel getPanel()
    {
        if (optionsPanel == null) {
            //panel = new jViConfigPanel(this);
            optionsPanel = new NbOptionsNode(new OptionsPanel.ChangeNotify() {
                @Override
                public void change()
                {
                    changed();
                }
            });
            if(ViManager.getOsVersion().isMac()) {
                // bug  2808635 "Unable to edit preferences on Mac OS X"
                // let it stretch out
                panel = optionsPanel;
            } else {
                // Put the options panel on the right side
                panel = new JPanel(new BorderLayout());
                panel.add(optionsPanel, BorderLayout.EAST);
                hack = new JLabel();
                panel.add(hack, BorderLayout.CENTER);

                // Let the options float in the middle of the dialog's area
                // instead of stretching the property sheet left/right.
                //panel = new JPanel();
                //panel.add(optionsPanel);
            }
        }
        return optionsPanel;
    }

    void changed()
    {
        if (!changed) {
            changed = true;
            pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
        }
        pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }
}
