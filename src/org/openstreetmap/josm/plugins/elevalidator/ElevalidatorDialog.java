package org.openstreetmap.josm.plugins.elevalidator;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;

public class ElevalidatorDialog extends ToggleDialog {

    public ElevalidatorDialog() {
        super(tr("Elevator Validator"), "elevator",
                tr("Toolbox for validating elevators"), null, 300, true);

        JPanel valuePanel = new JPanel(new GridLayout(1, 1));
        valuePanel.setBackground(Color.DARK_GRAY);
        JPanel jcontentpanel = new JPanel(new GridLayout(1, 0));
        final JTextField jTextField = new JTextField();
        valuePanel.add(jTextField);

        jcontentpanel.add(valuePanel);
    }
}
