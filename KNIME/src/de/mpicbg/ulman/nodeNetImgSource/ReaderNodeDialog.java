package de.mpicbg.ulman.nodeNetImgSource;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;

/**
 * <code>NodeDialog</code> for the "Image Receiver" Node.
 * It as an adopted code from the MyExampleNode found in the KNIME SDK.
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Vladimir Ulman, MPI-CBG.de
 */
public class ReaderNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring MyExampleNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected ReaderNodeDialog() {
        super();

        addDialogComponent(new DialogComponentNumber(ReaderNodeModel.createSettingsModel_Port(),
                           "TCP/IP port to listen at:", /*step*/ 1, /*componentwidth*/ 5));
        addDialogComponent(new DialogComponentNumber(ReaderNodeModel.createSettingsModel_TimeOut(),
                "Seconds to wait for initial connection:", /*step*/ 5, /*componentwidth*/ 5));
    }
}

