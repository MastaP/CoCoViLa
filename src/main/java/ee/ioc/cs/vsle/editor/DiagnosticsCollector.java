/**
 * 
 */
package ee.ioc.cs.vsle.editor;

/*-
 * #%L
 * CoCoViLa
 * %%
 * Copyright (C) 2003 - 2017 Institute of Cybernetics at Tallinn University of Technology
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.*;

import ee.ioc.cs.vsle.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for collecting error messages
 */
public class DiagnosticsCollector {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticsCollector.class);

    private int fatalMessagesCount = 0;
    
    private List<String> messages = new ArrayList<String>();

    /**
     * Collects messages
     * 
     * @param msg
     */
    public void collectDiagnostic( String msg ) {
        logger.debug( msg );

        messages.add( msg );
    }

    /**
     * Collects messages.
     * Some messages may be fatal
     * 
     * @param msg
     * @param isFatal
     */
    public void collectDiagnostic( String msg, boolean isFatal ) {
        
        collectDiagnostic( msg );
        
        if( isFatal ) {
            fatalMessagesCount++;
        }
    }
    
    /**
     * @return list of all collected messages
     */
    public List<String> getMessages() {
        return messages;
    }

    public void clearMessages() {
        messages.clear();
    }
    
    /**
     * @return true, if there were problems, false otherwise.
     */
    public boolean hasProblems() {
        return getMessages().size() > 0;
    }
    
    /**
     * if there are fatal messages, promptLoad() will always return false
     * 
     * @return false when there are no "fatal" messages collected,
     *         true otherwise
     */
    public boolean hasFatalCases() {
        return fatalMessagesCount > 0;
    }
    
    /**
     * Displays a modal dialog with list of messages. 
     * Allows users to make a choice either to continue or cancel.
     * If there are fatal messages in the list, method returns false.
     * 
     * @param relative
     * @param collector
     * @param title
     * @param source
     * @return true when the user chose to continue, false otherwise
     */
    public static boolean promptLoad( Component relative, DiagnosticsCollector collector, String title, String source ) {
        assert SwingUtilities.isEventDispatchThread();
        final JDialog dialog = new JDialog( relative != null ? SwingUtilities.getWindowAncestor( relative ) : null );
        dialog.setModal( true );
        dialog.setLocationRelativeTo( relative );
        dialog.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
        dialog.setTitle( title );

        JLabel topLabel = new JLabel( "The following problems occured " + "while loading the " + source + ":" );
        topLabel.setBorder( new EmptyBorder( 20, 5, 5, 5 ) );

        dialog.add( topLabel, BorderLayout.NORTH );

        JTextArea txt = new JTextArea( 5, 40 );
        txt.setBorder( new EmptyBorder( 5, 5, 5, 5 ) );
        txt.setLineWrap( true );
        txt.setWrapStyleWord( true );
        txt.setEditable( false );
        for ( String s : collector.getMessages() ) {
            txt.append( " - " );
            txt.append( s );
            txt.append( "\n" );
        }
        txt.setCaretPosition( 0 );
        JScrollPane scrollPane = new JScrollPane( txt, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER );
        dialog.add( scrollPane, BorderLayout.CENTER );

        final boolean[] result = new boolean[ 1 ];
        result[ 0 ] = false; // close is Cancel
        
        final JComponent focusable;
        
        JButton btnCancel = new JButton( "Cancel" );

        btnCancel.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e ) {
                dialog.setVisible( false );
            }
        } );
        
        JPanel btnPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT ) );
        
        if( collector.hasFatalCases() ) {
            focusable = btnCancel;
            
            btnCancel.setText( "Close" );
        }
        else { 
            final JButton btnContinue = new JButton( "Continue" );

            btnContinue.addActionListener( new ActionListener() {
                public void actionPerformed( ActionEvent e ) {
                    dialog.setVisible( false );
                    result[ 0 ] = true;
                }
            } );
            
            btnPanel.add( btnContinue );
            
            focusable = btnContinue;
        }

        btnPanel.add( btnCancel );
        dialog.add( btnPanel, BorderLayout.SOUTH );
        
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                focusable.requestFocus();
            }
        } );
        
        dialog.pack();
        dialog.setVisible( true );
        dialog.dispose();

        return result[ 0 ];
    }
    
    /**
     * Interface
     */
    public interface Diagnosable {
        public DiagnosticsCollector getDiagnostics();
    }
}
