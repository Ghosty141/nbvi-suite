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
import javax.swing.text.Segment;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.text.Line;
import org.openide.windows.IOProvider;
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
    ViTextView tv;
    OutputWriter ow;
    String tabTag;
    String fnTag;
    StringBuilder sb = new StringBuilder();
    boolean fHyperlink = true;
    
    /** Creates a new instance of NbOutputStream.
     * Type of ViOutputStream.OUTPUT is plain command output, the other
     * types will output lines from a file.
     */
    public NbOutputStream(ViTextView tv, String type, String info) {
        this.tv = tv;
        if(type.equals(ViOutputStream.OUTPUT)) {
            // plain output, no hyper text stuff or files or line numbers
            ow = getIO(false); // make a new tab
            ow.println(info);
        } else {
            String sep = type.equals(ViOutputStream.SEARCH) ? "/" : "";
            tabTag = "jVi " + sep +  info + sep;
            ow = getIO(true); // make a new tab
            fnTag = tv.getDisplayFileName() + ":";
        }
    }
    
    private OutputWriter getIO(boolean fNew) {
        return IOProvider.getDefault().getIO(tabTag, fNew).getOut();
    }

    public void println(int line, int offset, int length) {
        Segment seg = tv.getLineSegment(line);
        sb.setLength(0);
        sb.append(fnTag).append(line).append(":")
            .append(seg.array, seg.offset, seg.count - 1);
        try {
            if(fHyperlink) {
                Line nbLine = NbEditorUtilities.getLine(
                        tv.getEditorComponent().getDocument(),
                        tv.getLineStartOffset(line),
                        false);
                ow.println(sb.toString(), new OutList(nbLine, offset));
            } else {
                ow.println(sb.toString());
            }
        } catch (IOException ex) {
            // NEEDSWORK: Error manager stuff for exception
            ex.printStackTrace();
        }
    }

    public void println(String s) {
        ow.println(s);
    }

    public void close() {
        ow.close();
        ow = null;
        tv = null;
    }
    
    private static class OutList implements OutputListener {
        Line nbLine;
        int offset;
        
        OutList(Line nbLine, int offset) {
            this.nbLine = nbLine;
            this.offset = offset;
        }

        public void outputLineAction(OutputEvent outputEvent) {
            nbLine.show(Line.SHOW_TOFRONT, offset);
        }

        public void outputLineCleared(OutputEvent outputEvent) {
        }

        public void outputLineSelected(OutputEvent outputEvent) {
        }
    }

}
