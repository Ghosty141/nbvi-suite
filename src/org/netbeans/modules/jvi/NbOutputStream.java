/*
 * NbOutputStream.java
 *
 * Created on January 28, 2007, 1:16 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.OutputStreamAdaptor;
import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.ViTextView;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Segment;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.text.Line;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;
import org.openide.windows.OutputWriter;

/**
 * NbOutputStream is used to report the results of jVi commands such as
 * ":g/pattern/p" or ":1,$s/old/new/p".
 *
 * NEEDSWORK: NbOutputStream provide an option for hyperlink creation?
 * NEEDSWORK: NbOutputStream use NbEditorUtilities.getLine() to track links?
 *            Might as well give each line its own listener then.
 * Use Normal.nv_goto if textview still active/available.
 * NbEdUt.isDocumentActive(). Reopen file if needed.
 * @author erra
 */
public class NbOutputStream extends OutputStreamAdaptor {
    private static Logger LOG = Logger.getLogger(NbOutputStream.class.getName());
    String type;
    ViTextView tv;
    OutputWriter ow;
    String fnTag;
    StringBuilder sb = new StringBuilder();
    boolean fHyperlink = true;

    static int nOpen;
    static boolean checkOpenClose = true;
    
    /** Creates a new instance of NbOutputStream.
     * Type of ViOutputStream.OUTPUT is plain command output, the other
     * types will output lines from a file.
     * 
     * Priority in range 0 - 10 when 0 is lowest; only apply to OUTPUT.
     */
    public NbOutputStream(ViTextView tv,
                          String type,
                          String info,
                          int priority) {
        this.tv = tv;
        this.type = type;
        boolean bringUpWindow = priority > ViOutputStream.PRI_LOW;
        if(type.equals(ViOutputStream.OUTPUT)) {
            // plain output, no hyper text stuff or files or line numbers
            String tabTag = "jVi Output";
            ow = getIO(tabTag, false, bringUpWindow); // reuse tab
            ow.println("-----------------------------------------------------");
            if(info != null)
                ow.println(info);
        } else {
            String sep = type.equals(ViOutputStream.SEARCH) ? "/" : "";
            String tabTag = "jVi " + sep +  (info != null ? info : "") + sep;
            ow = getIO(tabTag, true, true); // make a new tab, always raise win
            fnTag = tv.getBuffer().getDisplayFileName() + ":";
        }
        checkOpen();
    }
    
    private OutputWriter getIO(String tabTag, boolean fNew, boolean fRaise) {
        InputOutput io = IOProvider.getDefault().getIO(tabTag, fNew);
        if(io.isClosed() && fRaise) {
            // See NetBeans Issue 101445
            try {
                io.getOut().reset();//io = IOProvider.getDefault().getIO(tabTag, true);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
                io = IOProvider.getDefault().getIO(tabTag, true);
            }
        }
        if(fRaise)
            io.select();
        return io.getOut();
    }

    @Override
    public void println(int line, int offset, int length) {
        if(type.equals(ViOutputStream.OUTPUT))
            return;
        Segment seg = tv.getBuffer().getLineSegment(line);
        sb.setLength(0);
        sb.append(fnTag).append(line).append(":")
            .append(seg.array, seg.offset, seg.count - 1);
        try {
            if(fHyperlink) {
                Line nbLine = NbEditorUtilities.getLine(
                        tv.getEditorComponent().getDocument(),
                        tv.getBuffer().getLineStartOffset(line),
                        false);
                ow.println(sb.toString(), new OutList(nbLine, offset));
            } else {
                ow.println(sb.toString());
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void println(String s) {
        ow.println(s);
    }

    @Override
    public void close() {
        ow.close();
        ow = null;
        tv = null;
        checkClose();
    }

    private synchronized static void checkOpen() {
        if(nOpen > 0) {
            System.err.println("UNBALANCED OPEN/CLOSE, RESETTING.");
            nOpen = 0;
        }
        nOpen++;
    }
    
    private synchronized static void checkClose() {
        nOpen--;
    }
    
    private static class OutList implements OutputListener {
        Line nbLine;
        int offset;
        
        OutList(Line nbLine, int offset) {
            this.nbLine = nbLine;
            this.offset = offset;
        }

        public void outputLineAction(OutputEvent outputEvent) {
            nbLine.show(Line.ShowOpenType.OPEN,
                        Line.ShowVisibilityType.FRONT,
                        offset);
        }

        public void outputLineCleared(OutputEvent outputEvent) {
        }

        public void outputLineSelected(OutputEvent outputEvent) {
        }
    }

}
