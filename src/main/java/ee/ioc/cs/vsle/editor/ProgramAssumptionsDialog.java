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

import java.lang.reflect.*;
import java.util.List;
import java.util.*;
import java.awt.*;
import java.awt.event.*;


import javax.swing.*;

import ee.ioc.cs.vsle.synthesize.*;
import ee.ioc.cs.vsle.util.*;
import ee.ioc.cs.vsle.vclass.*;
import static ee.ioc.cs.vsle.util.TypeUtil.*;

public class ProgramAssumptionsDialog extends JDialog 
implements ActionListener, KeyListener {
	
	Object[] args;
	
	List<JTextField> textFields = new ArrayList<JTextField>();
	List<JComboBox> comboBoxes = new ArrayList<JComboBox>();
	List<Var> primitiveNameList = new ArrayList<Var>();
	List<Var> arrayNameList = new ArrayList<Var>();
	List<Var> asumptions;
	JButton clear, ok;
	boolean isOK = false;
	
	public ProgramAssumptionsDialog( JFrame owner, String progName, List<Var> assmps ) {
		
		super( owner, progName, true );
		
		setDefaultCloseOperation( DISPOSE_ON_CLOSE );
		
		setLocationRelativeTo( getParent() );
		
		args = new Object[assmps.size()];
		asumptions = assmps;
		
		JPanel buttonPane = new JPanel();
		JPanel fullPane = new JPanel();
		
		JPanel labelPane = new JPanel();
		JPanel textFieldPane = new JPanel();
		JPanel typePane = new JPanel();
		
		
		labelPane.setLayout(new GridLayout(0, 1));
		typePane.setLayout(new GridLayout(0, 1));
		textFieldPane.setLayout(new GridLayout(0, 1));
		
		JLabel label;
		JTextField textField;
		JComboBox comboBox;
		
		for (Var var : assmps) {
			
			if (var.getField().isArray()) {
				comboBox = new JComboBox();
				comboBox.setEditable(true);
				comboBox.addActionListener(this);
				
				if (var.getField().getValue() != "" && var.getField().getValue() != null) {
					String[] split = var.getField().getValue().split( TypeUtil.ARRAY_TOKEN );
					for (int j = 0; j < split.length; j++) {
						comboBox.addItem(split[j]);
					}
				}
				JTextField jtf = (JTextField) (comboBox.getEditor().getEditorComponent());
				jtf.addKeyListener(this);
				
				
				comboBoxes.add(comboBox);
				label = new JLabel(var.getFullName(), SwingConstants.CENTER);
				label.setToolTipText(var.getField().getDescription());
				arrayNameList.add(var);
				labelPane.add(label);
				label = new JLabel("(" + var.getType() + ")");
				typePane.add(label);
				
				textFieldPane.add(comboBox);
			} else if (var.getField().isPrimitiveOrString()) {
				textField = new JTextField();
				textField.addKeyListener(this);
				textField.setName(var.getFullName());
				primitiveNameList.add(var);
				textField.setText(var.getField().getValue());
				textFields.add(textField);
				label = new JLabel(var.getFullName(), SwingConstants.CENTER);
				label.setToolTipText(var.getField().getDescription());
				labelPane.add(label);
				textFieldPane.add(textField);
				label = new JLabel("(" + var.getType() + ")");
				typePane.add(label);
			}
		}
		
		JPanel contentPane = new JPanel();
		JScrollPane areaScrollPane = new JScrollPane(contentPane,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		contentPane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		fullPane.setLayout(new BorderLayout());
		contentPane.setLayout(new GridLayout(0, 4));
		contentPane.add(labelPane);
		contentPane.add(textFieldPane);
		contentPane.add(typePane);
		ok = new JButton("OK");
		ok.addActionListener(this);
		buttonPane.add(ok);
		clear = new JButton("Clear all");
		clear.addActionListener(this);
		buttonPane.add(clear);
		fullPane.add(areaScrollPane, BorderLayout.CENTER);
		fullPane.add(buttonPane, BorderLayout.SOUTH);
		setContentPane(fullPane);
//		validate();
		
		setResizable( false );
		pack();
		setVisible( true );
	}
	
	public void keyTyped(KeyEvent e) {
		// ignore
	}
	
	public void keyReleased(KeyEvent e) {
		// ignore
	}
	
	public void keyPressed(KeyEvent e) {
		if (e.getKeyChar() == KeyEvent.VK_ENTER) {
			JTextField jtf = (JTextField) (e.getSource());
			JComboBox thisComboBox = getComboBox(jtf);
			if( thisComboBox != null ) {
				thisComboBox.addItem( jtf.getText() );
			}
		} else if (e.getKeyChar() == KeyEvent.VK_DELETE) {
			JTextField jtf = (JTextField) (e.getSource());
			JComboBox thisComboBox = getComboBox(jtf);
			if( thisComboBox != null ) {
				thisComboBox.removeItem( thisComboBox.getSelectedItem() );
			}
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		JTextField textField;
		Var var;
		//db.p(e);
		if (e.getSource() == ok) {
			for (int i = 0; i < textFields.size(); i++) {
				textField = textFields.get(i);
				var = primitiveNameList.get(i);
				
				String text = textField.getText();
				
				try {
					args[asumptions.indexOf( var )] = createObjectFromString( var.getType(), text );
				} catch (Exception e1) {
					showError( var.getName(), e1 );
					return;
				}
			}
			for (int i = 0; i < comboBoxes.size(); i++) {
				JComboBox comboBox = comboBoxes.get(i);
				var = arrayNameList.get(i);
				String s = "";
				for (int j = 0; j < comboBox.getItemCount(); j++) {
					s += (String) comboBox.getItemAt(j) + TypeUtil.ARRAY_TOKEN;
				}
				try {
					args[asumptions.indexOf( var )] = createObjectFromString( var.getType(), s );
				} catch (Exception e1) {
					showError( var.getName(), e1 );					
					return;
				}
			}
			isOK = true;
			this.dispose();
		}
		if (e.getSource() == clear) {
			// Clears object properties except the object name.
			for (int i = 0; i < textFields.size(); i++) {
				textField = textFields.get(i);
				textField.setText("");
			}
		}
	}
	
	private void showError( String var, Throwable th ) {
		String s = ( ( th != null ) && ( th.getCause() != null ) ) ? "\n" + th.getCause().getMessage() : "";
		
		JOptionPane.showMessageDialog( ProgramAssumptionsDialog.this, 
				"Unable to read assumption \"" + var + "\"" + s,
				"Error", JOptionPane.ERROR_MESSAGE );
	}
	
	JComboBox getComboBox(JTextField jtf) {
		for (int i = 0; i < comboBoxes.size(); i++) {
			JComboBox jcb = comboBoxes.get(i);
			if (jtf == jcb.getEditor().getEditorComponent()) {
				return jcb;
			}
		}
		return null;
	}

	public Object[] getArgs() {
		return args;
	}

	public boolean isOK() {
		return isOK;
	}
}
