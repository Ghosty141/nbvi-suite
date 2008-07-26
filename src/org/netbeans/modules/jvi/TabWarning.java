/*
 * TabWarning.java
 *
 * Created on September 12, 2007, 7:14 PM
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.MutableBoolean;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JDialog;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.openide.windows.WindowManager;

/**
 *
 * @author  erra
 */
public class TabWarning extends JDialog {
    
    // NEEDSWORK: put text into a non-edit text area, or html area
    // to allow copy of web site.

    private String getWarningMessage() {
        return "ShiftWidth, ExpandTabs and TabStop defaults"
             + " for jVi are set through\n"
             + "Tools>Options>AdvancedOptions>\n"
             + "jViOptionsAndConfiguration>FileModificationOptions.\n"
             + "Use \":set ...\" after file is opened.\n\n"
             + "See http://jvi.sourceforge.net/ReadmeNetBeans.html#options\n\n"
             + "jVi reprograms the indent/tab options per document,\n"
             + "so changes made in standard settings are lost and not used.\n\n"
             + "See also \"modeline\" in the vim documentation.";
    }
    
    /** Creates new form TabWarning */
    private TabWarning(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                quitDialog(false);
            }
        });
        
        // Using a JOptionPane to get proper look and feel,
        // but want it to be non-modal so need to find the "OK"
        // button and hook it up.
        JButton b = findButton(getContentPane());
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                quitDialog(true);
            }
        });
    }

    private void quitDialog(boolean doDispose) {
        if(tabWarning != null) {
            JDialog dialog = tabWarning;
            tabWarning = null;
            if(doDispose) {
                dialog.setVisible(false);
                dialog.dispose();
            }
        }
    }

    private static TabWarning tabWarning;
    private static Preferences prefs;
    private static PreferenceChangeListener scl;

    private static final MutableBoolean
            isInternalSetting = new MutableBoolean();

    static void setTabWarning(boolean enableFlag) {
        if(enableFlag) {
            if(scl == null) {
                scl = new TabSetListener();
                prefs = MimeLookup.getLookup(MimePath.EMPTY)
                        .lookup(Preferences.class);
                prefs.addPreferenceChangeListener(scl);
            }
        } else {
            if(scl != null) {
                prefs.removePreferenceChangeListener(scl);
                prefs = null;
                scl= null;
            }
        }
    }

    private static class TabSetListener implements PreferenceChangeListener {
        public void preferenceChange(PreferenceChangeEvent evt) {
            String settingName = evt == null ? null : evt.getKey();
            if(!isInternalSetting.getValue()
               && (settingName == null
                   || SimpleValueNames.SPACES_PER_TAB.equals(settingName)
                   || SimpleValueNames.EXPAND_TABS.equals(settingName)
                   || SimpleValueNames.TAB_SIZE.equals(settingName))
               ) {

                // Note there may be lots of events, but only one dialog
                if(tabWarning == null) {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            if(tabWarning == null) {
                                Frame f = WindowManager.getDefault()
                                                       .getMainWindow();
                                tabWarning = new TabWarning(f, false);
                                tabWarning.setLocationRelativeTo(f);
                                tabWarning.setVisible(true);
                            }
                        }
                    });
                }
            }
        }
    }

    static Timer timer;
    static TabSetTimerTask timerTask;
    private static final int DELAY = 1000; // one second

    /** This is used to signal that jVi is performing a setting and so
     * the dialog should not be put up. Simplest idea is to set flag while
     * jVi is calling the NB code, but this misses cases when there is delayed
     * actions, so add some hysteresis with a timer.
     * <br/>
     * Not using swing timer to insure no event thread interactions.
     */
    static void setInternalAction(boolean enable) {
        synchronized(isInternalSetting) {
            if(enable) {
                isInternalSetting.setValue(true);
            } else {
                //if(false) {
                //    isInternalSetting.setValue(false);
                //    return;
                //}
                if(timer == null)
                    timer = new Timer();
                if(timerTask != null) {
                    timerTask.cancel();
                }
                timerTask = new TabSetTimerTask();
                timer.schedule(timerTask, DELAY); // do it once
            }
        }
    }

    private static class TabSetTimerTask extends TimerTask {
        private boolean cancelled;

        @Override
        public void run() {
            synchronized(isInternalSetting) {
                if(!isCancelled()) {
                    isInternalSetting.setValue(false);
                    timerTask = null;
                    timer.cancel();
                    timer = null;
                    //System.err.println("FIRE");
                }
            }
        }
        
        @Override
        public boolean cancel() {
            synchronized(isInternalSetting) {
                cancelled = true;
                return super.cancel();
            }
        }

        // should be holding lock
        boolean isCancelled() {
            return cancelled;
        }
    }
    
    private static JButton findButton(Container top) {
        Container c01 = (Container)findButtonArea(top);
        JButton b = null;
        if(c01 != null) {
            for(int i = 0; i < c01.getComponentCount(); i++) {
                Component c = c01.getComponent(i);
                if(c instanceof JButton) {
                    JButton j01 = (JButton)c;
                    b = j01;
                }
            }
        }
        return b;
    }

    private static Component findButtonArea(Container container) {
        for(Component c : container.getComponents()) {
            if("OptionPane.buttonArea".equals(c.getName())) {
                return c;
            }
            if(c instanceof Container) {
                Component c01 = findButtonArea((Container)c);
                if(c01 != null)
                    return c01;
            }
        }
        return null;
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jOptionPane1 = new javax.swing.JOptionPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(org.openide.util.NbBundle.getMessage(TabWarning.class, "TabWarning.title")); // NOI18N

        jOptionPane1.setMessage(getWarningMessage());
        jOptionPane1.setMessageType(2);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jOptionPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jOptionPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                TabWarning dialog = new TabWarning(new javax.swing.JFrame(), true);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    public @Override void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JOptionPane jOptionPane1;
    // End of variables declaration//GEN-END:variables
    
}
