package org.cytoscape.diffusion.internal.ui;

import java.awt.Dimension;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.cytoscape.diffusion.internal.util.DiffusionNetworkManager;
import org.cytoscape.diffusion.internal.util.DiffusionTable;

/**
 * Created by sage on 12/9/2016.
 */
public class RankSelectionPanel extends JPanel {

    private static final Border TITLE = BorderFactory.createTitledBorder("Rank threshold");
    
    private JSlider thresholdSlider;

    private DiffusionTable diffusionTable;
    private DiffusionNetworkManager networkManager;

    RankSelectionPanel(DiffusionNetworkManager networkManager, DiffusionTable diffusionTable) {
        this.diffusionTable = diffusionTable;
        this.networkManager = networkManager;
        thresholdSlider = new JSlider(0, 1000);
        Hashtable labelTable = new Hashtable();
        labelTable.put(0, new JLabel("0.0"));
        System.out.println("Max heat");
        System.out.println(diffusionTable.getMaxRank());
        String maxHeat = String.format("%.2f", diffusionTable.getMaxHeat());
        labelTable.put(1000, new JLabel(maxHeat));
        thresholdSlider.setLabelTable(labelTable);
        thresholdSlider.setPaintLabels(true);
        thresholdSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                setThreshold(thresholdSlider.getValue());
            }
        });
        Integer ninteithPercentile = 900;
        System.out.println("Percentile was");
        System.out.println(ninteithPercentile);
        setThreshold(ninteithPercentile);
        thresholdSlider.setValue(ninteithPercentile);
        thresholdSlider.setInverted(true);
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.setBorder(TITLE);
        this.add(thresholdSlider);
        this.setMaximumSize(new Dimension(1000, 100));

    }

    private void setThreshold(Integer index) {
        System.out.println("Thresh set you");
        System.out.println(index);
        Double threshold = (diffusionTable.getMaxHeat()/1000)*index;
        System.out.println(threshold);
        networkManager.selectNodesOverThreshold(diffusionTable.getHeatColumnName(), threshold);
    }


}
