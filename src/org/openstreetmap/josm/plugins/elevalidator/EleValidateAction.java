package org.openstreetmap.josm.plugins.elevalidator;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

 /**
  * this import import is now necessary since package changed
  * compared to original ValidateAction.class
  */

import org.openstreetmap.josm.actions.JosmAction;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.preferences.sources.ValidatorPrefHelper;

import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.data.validation.util.AggregatePrimitivesVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainFrame;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.ValidatorLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.Shortcut;
import org.xml.sax.SAXException;

/**
 * The action that does the validate thing.
 * <p>
 * This action iterates through all active tests and give them the data, so that
 * each one can test it.
 */
public class EleValidateAction extends JosmAction {

    /** Last selection used to validate */
    private transient Collection<OsmPrimitive> lastSelection;

    /**
     * Constructor
     */
    public EleValidateAction() {
        super(tr("EleValidation"), "dialogs/validator", tr("Performs the elevator validation"),
                Shortcut.registerShortcut("tools:validate", tr("Tool: {0}", tr("Validation")),
                        KeyEvent.VK_V, Shortcut.SHIFT), true);
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        doValidate(true);
    }

    /**
     * Does the validation.
     * <p>
     * If getSelectedItems is true, the selected items (or all items, if no one
     * is selected) are validated. If it is false, last selected items are revalidated
     *
     * @param getSelectedItems If selected or last selected items must be validated
     */
    public void doValidate(boolean getSelectedItems) {
        MapFrame map = MainApplication.getMap();
        if (map == null || !map.isVisible())
            return;

        ElevatorValidator.initializeTests();

        Collection<Test> tests = ElevatorValidator.getEnabledTests(false);
        if (tests.isEmpty())
            return;

        Collection<OsmPrimitive> selection;
        if (getSelectedItems) {
            selection = getLayerManager().getActiveDataSet().getAllSelected();
            if (selection.isEmpty()) {
                selection = getLayerManager().getActiveDataSet().allNonDeletedPrimitives();
                lastSelection = null;
            } else {
                AggregatePrimitivesVisitor v = new AggregatePrimitivesVisitor();
                selection = v.visit(selection);
                lastSelection = selection;
            }
        } else {
            selection = Optional.ofNullable(lastSelection).orElseGet(
                    () -> getLayerManager().getActiveDataSet().allNonDeletedPrimitives());
        }

        MainApplication.worker.submit(new ValidationTask(tests, selection, lastSelection));
    }

    @Override
    public void updateEnabledState() {
        setEnabled(getLayerManager().getActiveDataSet() != null);
    }

    @Override
    public void destroy() {
        // Hack - this action should stay forever because it could be added to toolbar
        // Do not call super.destroy() here
        lastSelection = null;
    }

    /**
     * Asynchronous task for running a collection of tests against a collection of primitives
     */
    static class ValidationTask extends PleaseWaitRunnable {
        private Collection<Test> tests;
        private final Collection<OsmPrimitive> validatedPrimitives;
        private final Collection<OsmPrimitive> formerValidatedPrimitives;
        private boolean canceled;
        private List<TestError> errors;

        /**
         * Constructs a new {@code ValidationTask}
         * @param tests  the tests to run
         * @param validatedPrimitives the collection of primitives to validate.
         * @param formerValidatedPrimitives the last collection of primitives being validates. May be null.
         */
        ValidationTask(Collection<Test> tests, Collection<OsmPrimitive> validatedPrimitives,
                       Collection<OsmPrimitive> formerValidatedPrimitives) {
            super(tr("Validating"), false /*don't ignore exceptions */);
            this.validatedPrimitives = validatedPrimitives;
            this.formerValidatedPrimitives = formerValidatedPrimitives;
            this.tests = tests;
        }

        @Override
        protected void cancel() {
            this.canceled = true;
        }

        @Override
        protected void finish() {
            if (canceled) return;

            // update GUI on Swing EDT
            //
            GuiHelper.runInEDT(() -> {
                ElevalidatorDialog elevalidatorDialog = ElevalidatorPlugin.getElevalidatorDialog();
                elevalidatorDialog.unfurlDialog();
                elevalidatorDialog.tree.setErrors(errors);
                //FIXME: nicer way to find / invalidate the corresponding error layer
                MainApplication.getLayerManager().getLayersOfType(ValidatorLayer.class).forEach(ValidatorLayer::invalidate);
                if (!errors.isEmpty()) {
                    ElevatorValidator.initializeErrorLayer();
                }
            });
        }

        @Override
        protected void realRun() throws SAXException, IOException,
                OsmTransferException {
            if (tests == null || tests.isEmpty())
                return;
            errors = new ArrayList<>();
            getProgressMonitor().setTicksCount(tests.size() * validatedPrimitives.size());
            int testCounter = 0;
            for (Test test : tests) {
                if (canceled)
                    return;
                testCounter++;
                getProgressMonitor().setCustomText(tr("Test {0}/{1}: Starting {2}", testCounter, tests.size(), test.getName()));
                test.setBeforeUpload(false);
                test.setPartialSelection(formerValidatedPrimitives != null);
                test.startTest(getProgressMonitor().createSubTaskMonitor(validatedPrimitives.size(), false));
                test.visit(validatedPrimitives);
                test.endTest();
                errors.addAll(test.getErrors());
                test.clear();
            }
            tests = null;
            if (Boolean.TRUE.equals(ValidatorPrefHelper.PREF_USE_IGNORE.get())) {
                getProgressMonitor().setCustomText("");
                getProgressMonitor().subTask(tr("Updating ignored errors ..."));
                for (TestError error : errors) {
                    if (canceled) return;
                    error.updateIgnored();
                }
            }
        }
    }
}
