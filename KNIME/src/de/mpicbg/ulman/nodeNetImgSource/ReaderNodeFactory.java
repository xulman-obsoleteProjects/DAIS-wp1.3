package de.mpicbg.ulman.nodeNetImgSource;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "MyExampleNode" Node.
 * This is an example node provided by KNIME.com.
 *
 * @author KNIME.com
 */
public class ReaderNodeNodeFactory 
        extends NodeFactory<ReaderNodeNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ReaderNodeNodeModel createNodeModel() {
        return new ReaderNodeNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<ReaderNodeNodeModel> createNodeView(final int viewIndex,
            final ReaderNodeNodeModel nodeModel) {
        return new ReaderNodeNodeView(nodeModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new ReaderNodeNodeDialog();
    }

}

