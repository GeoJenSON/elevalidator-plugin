package org.openstreetmap.josm.plugins.elevalidator;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;


public class ElevalidatorPlugin extends Plugin {

    // Will be invoked by JOSM to bootstrap the plugin

    public static ElevalidatorDialog elevalidatorDialog = new ElevalidatorDialog();


    // @param info  information about the plugin and its local installation */
    public ElevalidatorPlugin(PluginInformation info) {
        super(info);
        // init your plugin
    }

    public static ElevalidatorDialog getElevalidatorDialog() {
        return elevalidatorDialog;
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        super.mapFrameInitialized(oldFrame, newFrame);

        if (oldFrame == null && newFrame != null) {
            newFrame.addToggleDialog(elevalidatorDialog);
        }
    }
}