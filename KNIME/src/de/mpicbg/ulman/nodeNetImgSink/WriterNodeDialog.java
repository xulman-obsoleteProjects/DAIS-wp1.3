package de.mpicbg.ulman.nodeNetImgSink;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.knip.base.data.img.ImgPlusValue;

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
public class WriterNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring MyExampleNode node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    @SuppressWarnings("unchecked")
	protected WriterNodeDialog() {
        super();

        addDialogComponent(new DialogComponentColumnNameSelection(WriterNodeModel.createSettingsModel_ImgColumn(),
                           "Choose image column to send: ", 0, ImgPlusValue.class));

        addDialogComponent(new DialogComponentNumber(WriterNodeModel.createSettingsModel_Port(),
                           "TCP/IP port to listen at:", /*step*/ 1, /*componentwidth*/ 5));
        addDialogComponent(new DialogComponentNumber(WriterNodeModel.createSettingsModel_TimeOut(),
                "Seconds to wait for initial connection:", /*step*/ 5, /*componentwidth*/ 5));
    }
}

