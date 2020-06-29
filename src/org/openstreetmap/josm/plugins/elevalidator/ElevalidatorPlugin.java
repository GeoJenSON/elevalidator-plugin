package org.openstreetmap.josm.plugins.elevalidator;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;


public class ElevalidatorPlugin extends Plugin {

    // Will be invoked by JOSM to bootstrap the plugin

    private static final Logger LOGGER = Logger.getLogger(ElevalidatorPlugin.class.getName());

    protected static ElevalidatorDialog elevalidatorDialog;

    // @param info  information about the plugin and its local installation */
    public ElevalidatorPlugin(PluginInformation info) {
        super(info);
        // init your plugin
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        super.mapFrameInitialized(oldFrame, newFrame);

        if (oldFrame == null && newFrame != null) {
            newFrame.addToggleDialog(elevalidatorDialog = new ElevalidatorDialog());
            //LOGGER.log(Level.INFO, elevalidatorDialog);
            System.out.println("Test logging");
        }
    }
}